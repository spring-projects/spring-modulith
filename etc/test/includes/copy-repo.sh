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

cd "$test_dir" || _exit 1 "Failed to change directory to $test_dir"
echo -e "\nIn $PWD"

owner=$(gh api user --jq '.login') || _exit 1 "Failed to get the current GitHub owner"
if [[ "$owner" == "spring-projects" ]]; then
  _exit 1 "GitHub owner cannot be 'spring-projects'"
fi

# Create a new empty target repository on GitHub
echo -e "\nDeleting and recreating $owner/$test_repo"
gh repo delete "$owner/$test_repo" --yes
gh repo create "$owner/$test_repo" --public || _exit 1 "Failed to create an empty target repository on GitHub"

# Clone the source repository
echo -e "\nCloning spring-projects/spring-modulith"
rm -rf "$test_repo"
gh repo clone spring-projects/spring-modulith "$test_repo" -- --no-tags || _exit 1 "Failed to clone repo spring-projects/spring-modulith"
cd "$test_repo" || _exit 1 "Failed to change directory into the cloned repo repository [$test_repo]"
echo -e "\nIn $(pwd)"
git rev-parse --is-inside-work-tree > /dev/null 2>&1 && git remote -v || _exit 1 "Not a git repository"

original_branch=$(git branch --show-current)

# Get list of desired branches
branches=()
regex='^[0-9]+\.[0-9]+\.x$'
branchCandidates=$(git branch -r | grep -v '\->' | sed 's/origin\///' | sed 's/^[ \t]*//;s/[ \t]*$//')
while IFS= read -r branchCandidate; do
  if [[ "$branchCandidate" =~ $regex ]]; then
    branches+=("$branchCandidate")
  fi
done < <(echo "$branchCandidates")
[ ${#branches[@]} -ne 0 ] || _exit 1 "Failed to identify candidate branches"

for branch in "${branches[@]}"; do
  echo -e "\nSwitching branches for checkout"
  git checkout --track "origin/$branch" || _exit 1 "Failed to check out branch $branch"
done

echo -e "\nSwitching back to original branch"
git checkout "$original_branch"

# Set origin url to new empty repo
echo -e "\nUpdate origin url to repo $test_repo"
current_url=$(git remote get-url origin)
if [[ "$current_url" == https* ]]; then
    new_url="https://github.com/$owner/$test_repo.git"
else
    new_url="git@github.com:$owner/$test_repo.git"
fi
git remote set-url origin "$new_url"

# Double check that origin does not point to spring-projects
current_url=$(git remote get-url origin)
[[ "$?" == 0 ]] || _exit 1 "Failed to get current git url"
[[ "$current_url" != *"spring-projects"* ]] || _exit 1 "The remote URL still contains 'spring-projects'"

# Push local branches to the new repository
branches=("$original_branch" "${branches[@]}")
for branch in "${branches[@]}"; do
  echo -e "\nSwitching branches for push"
  git checkout "$branch" || _exit 1 "Failed to check out branch $branch"
  git push -u origin "$branch" || _exit 1 "Failed to push branch $branch"
done

echo -e "\nSwitching back to default branch [$original_branch]"
git checkout "$original_branch"
gh repo edit --default-branch "$original_branch"

# Clean up
cd "$start_dir"

echo -e "\nSUCCESS: Created new repo [https://github.com/$owner/$test_repo], and cloned to $test_dir/$test_repo"
