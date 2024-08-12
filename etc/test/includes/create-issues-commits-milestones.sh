#!/bin/bash

# Check if values set globally by main script. If not, set here.
[ "$start_dir" != "" ] || start_dir=$(pwd)
[ "$script_dir" != "" ] || script_dir=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
[ "$functions" != "" ] || functions="$script_dir/../../functions.sh"

# Source the functions file
[ -x "$functions" ] || { echo -e "\nERROR: $functions is not an executable file"; exit 1; }
source "$functions"

test_dir=$(getTestDir)
test_repo=$(getTestRepoName)

cd "$test_dir/$test_repo" || _exit 1 "Failed to change directory to $test_dir/$test_repo"
echo -e "\nIn $(pwd)"
git rev-parse --is-inside-work-tree > /dev/null 2>&1 && git remote -v || _exit 1 "Not a git repository"

echo -e "\nCreating new issue"
url=$(gh issue create --label "bug" --title "Buggy bug" --body "When I try to do this thing, it breaks" | grep "https")
number="$(echo "$url" | awk -F/ '{print $NF}')"
sourceGh=$(getGHCode "$number")
echo "Created issue $number [$sourceGh]"

echo -e "\nCreating commits, some for issue $number [$sourceGh]"
for i in {1..3}; do
  # Make issue-related commit
  filename1="file-$number-$i.md"
  echo "Part $i of the fix for issue $number" > $filename1
  git add $filename1
  git commit -m "$sourceGh - Fix part $i"
  # Make non-related commit
  filename2="file-0-$i.md"
  echo "This line is NOT related to issue [$number-$i]" >> $filename2
  git add $filename2
  git commit -m "Updates not related to issue [$number-$i]"
done
# push changes
git push

echo -e "\nCreating milestones"
milestones=$(gh api repos/spring-projects/spring-modulith/milestones --jq '.[].title')
echo "$milestones" | while IFS= read -r milestone; do
  if [ "$milestone" != "" ]; then
    result="$(gh api -X POST repos/:owner/:repo/milestones -f title="$milestone" -f state="open" -f description="" -f due_on=$(date -u -v +1y +"%Y-%m-%dT%H:%M:%SZ") 2>/dev/null)"
    retVal="$?"
    if [[ "$retVal" != 0 && "$(echo "$result" | jq -r '.errors[0].code')" == "already_exists" ]]; then
      echo "Milestone [$milestone] already exists"
    else
      echo "Created milestone [$milestone]"
    fi
  fi
done

# Clean up
cd "$start_dir"

echo -e "\nSUCCESS: Created new issue [$url], with commits and milestones, using clone in $test_dir/$test_repo"
