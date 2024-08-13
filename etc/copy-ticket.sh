#!/bin/bash

sourceGh="GH-$1"
json=$(gh issue view $1 --json=title,labels)

title=$(echo $json | jq -r '.title')
labels=$(echo $json | jq -r '.labels[].name' | paste -sd ',' -)

gh issue create --title "$title" \
  --body "Back-port of $sourceGh." \
  --label "$labels" \
  --assignee "@me" \
  --milestone "$2"
