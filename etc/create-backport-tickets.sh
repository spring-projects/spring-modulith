#!/bin/bash
# Format $ticketNumer $targetVersion[]

sourceGh="GH-$1"
branch=$(git branch --show-current)
json=$(gh issue view $1 --json=title,labels)

title=$(echo $json | jq -r '.title')
labels=$(echo $json | jq -r '.labels[].name' | paste -sd ',' -)

number=$1

# For each of the target versions
for version in ${@:2}
do
	# Turn 1.5.6 into 1.5.x
	targetBranch="$(echo "$version" | grep -oE '^[0-9]+\.[0-9]+').x"

	# Checkout target branch and cherry-pick commit
	echo "Checking out branch $targetBranch"
	git checkout $targetBranch

	echo "Cherry-pick commit from $branch"
	git cherry-pick $branch

	echo "gh issue create --title \"$title\" --body \"Back-port of $sourceGh.\" --label \"$labels\" --assignee \"@me\" --milestone \"$version\""
	number=$(gh issue create --title "$title" --body "Back-port of $sourceGh." --label "$labels" --assignee "@me" --milestone "$version" | awk -F '/' '{print $NF}')
	echo "New ticket number: $number"

	# Replace ticket reference with new one
	targetGh="GH-$number"
	expression="s/$sourceGh/$targetGh/g"
	message=$(git log -1 --pretty=format:"%s" | sed $expression)

	# Update commit message to refer to new ticket
	echo "Adapt commit message from $sourceGh to $targetGh"
	git commit --amend -m "$message"
done

# Return to original branch
git checkout $branch
