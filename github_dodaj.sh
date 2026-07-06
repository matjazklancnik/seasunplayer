#!/bin/bash

MSG="${1:-update}"

echo
echo "======================================="
echo "GIT UPDATE START"
echo "======================================="
echo

git status

echo
echo "ADDING FILES..."
git add .

echo
echo "COMMITTING..."

git commit -m "$MSG"

if [ $? -ne 0 ]; then
    echo
    echo "NO CHANGES TO COMMIT"
    exit 0
fi

echo
echo "PULLING LATEST..."
git pull --rebase

echo
echo "PUSHING..."
git push

echo
echo "======================================="
echo "DONE"
echo "======================================="
echo
