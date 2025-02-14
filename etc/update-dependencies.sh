#!/bin/bash
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
mapping_file="${SCRIPT_DIR}/mappings.txt"
mvn_output_file="${SCRIPT_DIR}/../target/updates.txt"

# Check if version type is provided
if [ -z "$1" ] || [[ ! "$1" =~ ^(bugfix|minor|major)$ ]]; then
    echo "Usage: $0 <bugfix|minor|major>"
    exit 1
fi

# Check GH milestones extension installed
if [ -z "$(gh extension list | grep "^gh milestone")" ]; then
    echo "gh milestones extension not installed. Install via: gh extension install valeriobelli/gh-milestone."
    exit 1;
fi

# Set Maven flags based on version type
case "$1" in
    "bugfix")
        update_flags="-DallowMinorUpdates=false"
        ;;
    "minor")
        update_flags="-DallowMajorUpdates=false"
        ;;
    "major")
        update_flags=""
        ;;
esac

#
# List possible updates
#
./mvnw versions:display-property-updates -q -N \
    $update_flags \
    -Dversions.outputFile="$mvn_output_file"

matches_found=0

# Process the output file with the regex
while IFS= read -r line; do

    if [[ $line =~ \$\{([[:alnum:]-]+)\.version\}[[:space:]\.]*([0-9]+\.[0-9]+\.[0-9]+.*)[[:space:]]\-\>[[:space:]]([0-9]+\.[0-9]+\.[0-9]+.*) ]]; then

        echo "---"
        echo "Processing line: $line"

        property_name="${BASH_REMATCH[1]}"
        old_version="${BASH_REMATCH[2]}"
        new_version="${BASH_REMATCH[3]}"

        # Skip if new version matches pattern (case insensitive)
        if echo "$new_version" | grep -qiE "(-m[0-9]|-rc[0-9]|alpha|beta)"; then
            echo "❌ - Skipping preview version: $new_version."
            echo "---"
            continue
        fi

        # Look up the mapping directly
        mapping=$(grep "^${property_name}=" "$mapping_file" | cut -d '=' -f2)
        
        if [ -z "$mapping" ]; then

            echo "❌ - No mapping found for property: $property_name. Skipping."
            echo "---"
            continue
        fi 

        # On first match
        if [ $matches_found -eq 0 ]; then


            #
            # Detect target version
            #

            # Output local version
            localVersion=$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout | sed 's/\([0-9]*\.[0-9]*\)\..*/\1/')
            remoteVersions=$(gh milestone list --json title --jq ".[].title")

            # Match against GitHub milestones (select the last match, to make sure we use the direct next version in case of multiple milestones)
            targetVersion=$(gh milestone list --json title --jq ".[].title" | grep "^$localVersion" | tail -1)

            if [ -z "$targetVersion" ]; then
                echo "No target version detected"
                exit 1;
            else
                echo "Detected target version $targetVersion."
            fi
        fi

        matches_found=$((matches_found + 1))   

        creationResult=$(gh issue create \
            --title "Upgrade ${mapping} to ${new_version}" \
            --body "" \
            --label "in: infrastructure,type: dependency-upgrade" \
            --assignee "@me" \
            --milestone "${targetVersion}")
        
        # Create GitHub issue and capture the issue number
        issue_title="Upgrade ${mapping} to ${new_version}"
        issue_number=$(echo $creationResult | grep -o '[0-9]*$')
        
        echo "Created GitHub issue GH-${issue_number} - ${issue_title}."
        
        # Update the version using Maven versions plugin
        ./mvnw versions:set-property -q \
            -DgenerateBackupPoms=false \
            -Dproperty=${property_name}.version \
            -DnewVersion=${new_version}
        
        echo "Updated ${property_name}.version from ${old_version} to ${new_version}."
        
        # Commit the change with the issue number
        git add pom.xml
        git commit -m "GH-${issue_number} - Update ${mapping} to ${new_version}."
        
        # Push changes to remote
        # git push
        
        # Close the issue
        # gh issue close ${issue_number}
        
        # echo "✅ Pushed changes and closed issue GH-${issue_number}"
        echo "---"
    fi
done < "$mvn_output_file"

if [ $matches_found -eq 0 ]; then
    echo "No version updates found for $1 updates."
fi

# Clean up temporary file
# rm "$mvn_output_file"