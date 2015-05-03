#!/bin/sh

# svn2git conversion script for SLRoadtrip: J. Monin 2015-05-03
#   Downloads the remote svn repo, converts it to a new local git repo
#   in the current directory.  Run it from an empty directory only.
#   Appends svn commit numbers to the git comments, such as [svn r398]
#   Uses ruby gem svn2git from https://github.com/nirvdrum/svn2git
#
# This file Copyright (C) 2015 Jeremy D Monin <jdmonin@nand.net>

# Process and prereqs:
# - ruby and the svn2git gem are installed
# - svn status shows no uncommitted local changes
# - the repo contents' svn properties have been checked for svn:ignore and others:
#	$ svn proplist -Rv
#	$ svn pg -R svn:ignore
# - Repo's list of authors has been scanned, and an authors.txt file prepared:
#	$ svn log -q
#	for SLRoadtrip, the file is:
#	jdmonin@nand.net = Jeremy D Monin <jdmonin@nand.net>
#	(no author) = Jeremy D Monin <jdmonin@nand.net>
#	If you have many authors, you can get a list of them with:
#	$ svn log -q | grep -e '^r' | awk 'BEGIN { FS = "|" } ; { print $2 }' | sort | uniq
#	(adapted from https://jaibeermalik.wordpress.com/2013/10/23/svn2git-migrating-repository-from-subversion-to-git/)
# - This script assumes the repo isn't using branches or tags: if yours is, then in
#	the svn2git command in this script, remove --notags and/or --nobranches
# - For the commit message rewrites, a temp directory on a fast disk has been created
# - Run this script from an empty directory (`pwd` contains no files)

# After running this script successfully:
# - Use gitk or GitX to review the conversion
# - Check out a fresh copy of master from .git, and diff -ur against the svn working directory
# - You can tag past releases, if not already tagged by svn2git, using their commit hash:
#	$ GIT_COMMITTER_DATE="2015-04-27 08:07" git tag -a -f release-0.9.41 -m 'Version 0.9.41 is r428 - tested Apr 27 til May 2' 06a4508
#	In GIT_COMMITTER_DATE the time is HH:MM in your computer's local timezone.
#	Github will show these tags under Releases including the date, time, and comment.
# - When you are satisified that everything validates, create a new repo on github
# - Finally, add a remote and push to github:
#	$ git remote add origin git@github.com:jdmonin/SLRoadtrip.git
#	$ git push --force origin
#	$ git push --force --tags origin

# Observations:
# - OSX 10.9's built-in ruby 2.0.0 and its gems are adequate to run the conversion

# Adjustable parameters:
SVN_REPO_URL=http://shadowlands-roadtrip.googlecode.com/svn/
AUTHOR_FILE=`dirname $0`/../../proj/git2svn-authors.txt
MSG_REWRITE_TEMPDIR=/Volumes/RAMDisk

# Script begins.  No need to change variables below this line.

MSG_REWRITE_SUBDIR=$MSG_REWRITE_TEMPDIR/slroadtrip-gitrewrite

if [ "$(ls -A .)" ]; then
  echo "Stopping: Current directory must be empty."
  exit 1
fi

if [ ! -f $AUTHOR_FILE ]; then
  echo "Stopping: Author file not found: $AUTHOR_FILE"
  exit 1
fi

if [ ! -d $MSG_REWRITE_TEMPDIR ]; then
  echo "Stopping: Missing message-rewrite temp dir: $MSG_REWRITE_TEMPDIR"
  exit 1
fi

# test mkdir now for git filter-branch, instead of failing several minutes later.
# remove it here because git filter-branch will create it again.
mkdir $MSG_REWRITE_SUBDIR
if [[ $? != 0 ]]; then
  echo "Could not create temp subdirectory $MSG_REWRITE_SUBDIR"
  exit 1
fi
rmdir $MSG_REWRITE_SUBDIR

echo "SVN repository URL: $SVN_REPO_URL"
date
echo "Beginning conversion into new git repo in current directory."
echo ""

# Note: The simple SLRoadtrip svn repo didn't use tags or branches;
# if you're using this for another project and it does use those, then
# remove --notags and/or --nobranches as appropriate.
svn2git $SVN_REPO_URL --metadata --notags --nobranches --verbose  --authors $AUTHOR_FILE

SVN2GIT_RC=$?

echo ""
date
echo "svn2git exit code: $SVN2GIT_RC"

if [[ $SVN2GIT_RC != 0 ]]; then
  echo "Stopping.  Please examine the svn2git output for the error, resolve, and re-run."
  exit 1
fi

# Simplify commit comments, preserving svn commit numbers.
#	svn2git --metadata creates them with this format:
# logbook_menu.xml: Move Validate/Export out of submenu; mark for ActionBar
#
# git-svn-id: http://shadowlands-roadtrip.googlecode.com/svn/trunk@398 acf33098-bec4-aaee-49c5-08e01563f395
#
#	Convert now to this format:
# logbook_menu.xml: Move Validate/Export out of submenu; mark for ActionBar [svn r398]

git filter-branch -d $MSG_REWRITE_SUBDIR \
  --msg-filter 'perl -p -0777 -e '"'"' s/(.+?)\s+git-svn-id: [^@]*@([0-9]*)(\s.*)$/$1 [svn r$2]/m '"'"' ' \
  -- --all

REWRITE_RC=$?

echo ""
date
if [[ $REWRITE_RC != 0 ]]; then
  echo "Commit message adjustment failed."
  exit 1
fi
echo "Successfully adjusted commit messages."

# Since this is a 1-time conversion, remove the svn remote:
# (slroadtrip is a simple project with no svn branches; if your project
#  has branches, you will need to remove them all.)
# Script ignores failures here, because that can be cleaned up manually.
git branch -rd svn/trunk

git config --unset svn-remote.svn.url
git config --unset svn-remote.svn.fetch
git config --unset svn.authorsfile

# Project-specific settings cleanup after svn2git:
git config core.ignorecase false

# Clean up: reduce size if possible; failures are ok
echo ""
echo "Running repack and gc to reduce repository size."
git repack -a -d -f --window=50 --depth=50
git gc --aggressive --prune=now


# All done.
echo ""
echo "** Conversion is complete."
echo "** Please validate the contents of the new git repository."

exit 0
