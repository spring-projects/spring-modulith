#!/bin/bash

# main

start_dir=$(pwd)
script_dir=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
functions="$script_dir/../functions.sh"

source "$script_dir/includes/copy-repo.sh"
source "$script_dir/includes/create-issues-commits-milestones.sh"

echo "DONE"
