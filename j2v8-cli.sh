#!/bin/bash
# This script adds aliases for some of the most often used commands for building J2V8
# to your current command-shell instance. (can be invoked as "source j2v8-cli.sh")

if command -v python &>/dev/null; then
	alias build="python build.py"
	alias nodejs="python nodejs.py"
	alias citests="python build_system/run_tests.py"
else
	 echo Python 2 is not installed
fi
