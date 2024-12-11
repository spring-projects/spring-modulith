#!/bin/bash

start_dir=$(pwd)
script_dir=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")

# Source the functions file
functions="$script_dir/functions.sh"
[ -x "$functions" ] || { echo -e "\nERROR: $functions is not an executable file"; exit 1; }
source "$functions"

test_filter=""
if [[ "$SPRING_MODULITH_BACKPORT_TICKET_TEST_MODE_ENABLED" == "true" ]]; then
  echo -e "\nTest mode is enabled"
  test_dir=$(getTestDir)
  test_repo=$(getTestRepoName)
  cd "$test_dir/$test_repo" || _exit 1 "Failed to change directory to $test_dir/$test_repo"
  current_url=$(git remote get-url origin)
  [[ "$?" == 0 ]] || _exit 1 "Failed to get current git url"
  [[ "$current_url" != *"spring-projects"* ]] || _exit 1 "Remote URL cannot contain 'spring-projects' when test mode is enabled"
  # Including a "since" date to enable test mode, wherein a copy of the repo is created (see test scripts)
  #   Issues created in the test env start with 1, which would be matched with old commits rather than new "test" commits
  #   Setting this date to "2023-11-01" will enable backporting open issue 345 if necessaey
  #   Once that issue is closd this date can also be safely set to June 5 (GH-659), Jul 5 (GH-704), or later if these are closed by then
  test_filter="--since=\"2023-11-01\""
fi

# Check that at least two inputs were provided
# Format $ticketNumber $targetVersion1 $targetVersion2 ... $targetVersionN
isBlank "$1" || isBlank "$2" && _exit 1 "Two inputs are required: ticketNumber targetVersion[]"

# Check that first input is valid
echo -e "\nChecking that the first input is valid"
number=$1
isValidIssueNumber "$number" || _exit 1 "The provided issue number is invalid"

# Check that second input is valid
echo -e "\nChecking that the second input is valid"
# Convert the second input to an array and check each element
versions=("${@:2}")
for version in "${versions[@]}"; do
  isValidVersionNumber "$version" || _exit 1 "The provided version number [$version] is invalid"
done

echo -e "\nChecking the state of the current branch"

isDefaultBranch || _exit 1 "Current branch is not the default branch"
# isCleanBranch || _exit 1 "Current branch is not clean"
# To repair, run: git cherry-pick --abort &>/dev/null; git fetch origin && git reset --hard origin/$(git symbolic-ref --short HEAD) && git clean -fd; git checkout main

sourceGh=$(getGHCode "$number")
branch=$(git branch --show-current)

echo -e "\nGathered working values:"
echo "sourceGh=$sourceGh"
echo "branch=$branch"
echo "test_filter=$test_filter"

# The SHAs of all commits associated with the source ticket
echo -e "\nCapturing commits for $sourceGh:"
if [[ "$test_filter" == "" ]]; then
  git log --grep="\<$sourceGh\>" --reverse
  shas=$(git log --grep="\<$sourceGh\>" --reverse --format="%H")
else
  git log --grep="\<$sourceGh\>" "$test_filter" --reverse
  shas=$(git log --grep="\<$sourceGh\>" "$test_filter" --reverse --format="%H")
fi

echo -e "\nshas=\n$shas"

# For each of the target versions
for version in "${versions[@]}"
do
	# Turn 1.5.6 into 1.5.x
	targetBranch=$(getTargetBranch "$version")

	# Checkout target branch and cherry-pick commit
	echo -e "\nChecking out target branch"

	git checkout $targetBranch
	# isCleanBranch || _exit 1 "Current branch is not clean"
    # To repair, run: git cherry-pick --abort &>/dev/null; git fetch origin && git reset --hard origin/$(git symbolic-ref --short HEAD) && git clean -fd; git checkout main

  targetGh=""
  targetMilestone=$(getTargetMilestone "$version")

	# Cherry-pick all previously found SHAs
  while IFS= read -r sha
  do

    echo -e "\nCherry-pick commit $sha from $branch"
    git cherry-pick "$sha"
    retVal=$?
    [ "$retVal" == 0 ] || _exit 1 "Cherry-pick of commit $sha failed with return code $retVal"

    if isBlank "$targetGh"; then
      targetCandidateNumbers=$(getIssueCandidatesForMilestone "$number" "$targetMilestone")
      IFS=$'\n' read -rd '' -a array <<< "$targetCandidateNumbers"
      countTargetCandidateNumbers=${#array[@]}
      [ $countTargetCandidateNumbers -lt 2 ] || _exit 1 "Found multiple candidate target issues [$targetCandidateNumbers] for milestone [$targetMilestone]"
      if [ $countTargetCandidateNumbers -eq 1 ]; then
        #targetNumber=$(echo "$targetCandidateNumbers" | tr -d '\n')
        targetNumber="$targetCandidateNumbers"
        echo -e "\nRetrieved existing open target issue [$targetNumber] for milestone [$targetMilestone]"
        isCleanIssue "$targetNumber" || _exit 1 "Target issue [$targetNumber] is not clean"
      else
        # count is 0, create a new issue
        targetNumber=$(createIssueForMilestone "$number" "$targetMilestone")
        isBlank "$targetNumber" && _exit 1 "Failed to create a new target issue for milestone [$targetMilestone]"
        echo -e "\nCreated new target issue [$targetNumber] for milestone [$targetMilestone]"
      fi
      targetGh=$(getGHCode "$targetNumber")
    fi

    # Replace ticket reference with new one
    updateCommitMessage "$sourceGh" "$targetGh"
    echo "Updated commit message"

  done <<< "$shas"

done

# Return to original branch
git checkout "$branch"

cd "$start_dir"
