# functions.sh

# _exit should be called from the main script only. If called from
# another function, it will not cause the main script to exit
_exit() {
  local exit_code="$1"
  local message="$2"
  if [ "$exit_code" -ne 0 ]; then
    echo -e "\nERROR: $message"
  else
    echo -e "\n$message"
  fi
  echo "Exiting script [exit_code=$exit_code]"
  exit "$exit_code"
}

getTestDir() {
  echo "/tmp"
}

getTestRepoName() {
  echo "spring-modulith-temp-copy"
}

isBlank() {
  [[ "$1" =~ ^[[:space:]]*$ ]]
}

isValidIssueNumber() {
  local issue="$1"
  # Check if the provided number matches an existing issue number
  # Note: The issue status should be "open" (backporting is done before an issue is closed)
  #       But it is not necessary to filter on status - it is preferable not to constrain
  if ! gh issue list --limit 10000 --state "all" --json number --jq '.[].number' | grep -x -q "^$issue$"; then
    echo "The provided issue number [$issue] does not match an existing GitHub issue number"
    # output value
    false
  else
    echo "The provided issue number [$issue] matches an existing GitHub issue number"
    # output value
    true
  fi
}

isValidVersionNumber() {
  local version="$1"
  # Check input format
  local regex='^[0-9]+\.[0-9]+\.[0-9]+$'
  if [[ "$version" =~ $regex ]]; then
    echo "Version [$version] matches the required format"
    # Check for branch
    local targetBranch=$(getTargetBranch "$version")
    local branch=$(git ls-remote --heads origin "$targetBranch")
      if [[ -n "$branch" ]]; then
        echo "Branch [$targetBranch] exists"
        # Check for milestone
        local targetMilestone=$(getTargetMilestone "$version")
        local milestone=$(gh api repos/:owner/:repo/milestones --jq ".[] | select(.title == \"$targetMilestone\") | .title")
        if [[ -n "$milestone" ]]; then
          echo "Milestone [$targetMilestone] exists"
          true
        else
          echo "Milestone [$targetMilestone] does not exist"
          false
        fi
      else
        echo "Branch [$targetBranch] does not exist"
        false
      fi
  else
    echo "Version [$version] does not match the required format [$regex]"
    false
  fi
}

getTargetBranch() {
  local version="$1"
  local targetBranch="$(echo $version | grep -oE '^[0-9]+\.[0-9]+').x"
  echo "$targetBranch"
}

getTargetMilestone() {
  local version="$1"
  local targetMilestone="$version"
  echo "$targetMilestone"
}

isDefaultBranch() {
  local current_branch=$(git rev-parse --abbrev-ref HEAD)
  local default_branch=$(gh repo view --json defaultBranchRef --jq '.defaultBranchRef.name')
  if [ "$current_branch" != "$default_branch" ]; then
    echo "Current branch [$current_branch] is NOT the default branch [$default_branch]"
    # output value
    false
  else
    echo "Current branch [$current_branch] is the default branch"
    # output value
    true
  fi
}

isCleanBranch() {
  local branch=$(git symbolic-ref --short HEAD)
  local remote_branch="origin/$branch"

  # Check for ongoing cherry-pick
  if [ -d .git/sequencer ]; then
    echo "Error: Ongoing cherry-pick operation detected."
    # return code
    return 1
  fi

  # Check for uncommitted changes
  if ! git diff-index --quiet HEAD --; then
    echo "Error: Uncommitted changes in the working directory."
    # return code
    return 1
  fi

  # Check for untracked files and directories
  if [ -n "$(git clean -fdn)" ]; then
    echo "Error: Untracked files or directories present."
    # return code
    return 1
  fi

  # Fetch latest changes from the remote
  git fetch origin &>/dev/null

  # Check if local branch is ahead/behind the remote branch
  local local_status=$(git rev-list --left-right --count ${branch}...${remote_branch})
  local ahead=$(echo $local_status | awk '{print $1}')
  local behind=$(echo $local_status | awk '{print $2}')

  if [ "$ahead" -ne 0 ]; then
    echo "Error: Local branch is ahead of the remote branch by $ahead commit(s)."
    # return code
    return 1
  elif [ "$behind" -ne 0 ]; then
    echo "Error: Local branch is behind the remote branch by $behind commit(s)."
    # return code
    return 1
  fi

  # If all checks pass
  echo "Local branch matches the remote branch."
  # return code
  return 0
}

getGHCode() {
  echo "GH-$1"
}

getIssueCandidatesForMilestone() {
  local number="$1"
  local milestone="$2"

  local json=$(gh issue view "$number" --json=title,labels)
  local title=$(echo "$json" | jq -r '.title')
  local labels=$(echo "$json" | jq -r '.labels[].name' | paste -sd ',' -)
  local body="Back-port of $(getGHCode $number)."

  local targetCandidateNumbers=$(gh issue list --limit 10000 --state "open" --assignee "@me" --label "$labels" --milestone "$targetMilestone" --json number,title,body --jq '
     .[] | select(.title == "'"$title"'" and (.body | contains("'"$body"'")) ) | .number')
  echo "$targetCandidateNumbers"
}

createIssueForMilestone() {
  local number="$1"
  local milestone="$2"

  local json=$(gh issue view "$number" --json=title,labels)
  local title=$(echo "$json" | jq -r '.title')
  local labels=$(echo "$json" | jq -r '.labels[].name' | paste -sd ',' -)
  local body="Back-port of $(getGHCode $number)."

  local targetNumber=$(gh issue create --assignee "@me" --label "$labels" --milestone "$targetMilestone" --title "$title" --body "$body" | awk -F '/' '{print $NF}')
  echo "$targetNumber"
}

isCleanIssue() {
  local targetNumber="$1"
  local targetGh=$(getGHCode "$targetNumber")

  # Check for commits mentioning the issue number
  # $test_filter set globally in calling script
  local commits
  if [[ "$test_filter" == "" ]]; then
    commits=$(git log --grep="\b$targetGh\b")
  else
    commits=$(git log --grep="\b$targetGh\b" "$test_filter")
  fi
  if [ -z "$commits" ]; then
    # There are no commits that reference this issue
    # output value
    true
  else
    # output value
    false
  fi
}

updateCommitMessage() {
  local source="$1"
  local target="$2"
  local message=$(git log -1 --pretty=format:"%B" | sed "s/$source/$target/g")
  if [[ $(echo $message | grep "$target") != "" ]]; then
    # Update commit message to refer to new ticket
    git commit --amend -m "$message"
    [ "$?" -eq 0 ] || return 1
    return 0
  else
    return 1
  fi
}
