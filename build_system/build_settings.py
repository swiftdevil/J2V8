"""
The values specified here will be used as the single source of truth for
version strings and other globally shared build variables that should be
used in the build-process (build.py) or the Node.js utility scripts (nodejs.py)
"""

import os

#-----------------------------------------------------------------------
# Node.js settings
#-----------------------------------------------------------------------
# [user-setting] you can change this if you plan to use a different Node.js version for the J2V8 build
NODE_VERSION_MAJOR, NODE_VERSION_MINOR, NODE_VERSION_PATCH = 7, 9, 0

# The Node.js version in the format {major.minor.patch} to be used in other build & utility scripts
NODE_VERSION = '{}.{}.{}'.format(NODE_VERSION_MAJOR, NODE_VERSION_MINOR, NODE_VERSION_PATCH)

#-----------------------------------------------------------------------
# J2V8 settings
#-----------------------------------------------------------------------

J2V8_VERSION_MAJOR, J2V8_VERSION_MINOR, J2V8_VERSION_PATCH = 5, 3, 0
#J2V8_VERSION_SUFFIX = "-SNAPSHOT"
J2V8_VERSION_SUFFIX = ""

# The J2V8 version in the format {major.minor.patch} to be used in other build & utility scripts
J2V8_VERSION = '{}.{}.{}'.format(J2V8_VERSION_MAJOR, J2V8_VERSION_MINOR, J2V8_VERSION_PATCH)

# The J2V8 version including a version suffix string (e.g. 1.0.0-SUFFIX)
J2V8_FULL_VERSION = J2V8_VERSION + J2V8_VERSION_SUFFIX

# The path where other lib builds live. These will get included in the final JAR
TARGETS_LIB_DIR = os.environ['HOME'] + "/src/j2v8_targets"
