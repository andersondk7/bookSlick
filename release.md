# How to do a release

Development flow:
1. checkout from develop to do a feature/story
   2. update version to snapshot
2. merge back into develop when tested
    3. update version to non-snapshot
4. repeat as new features are added

At some point a release is needed:
1. create a release branch off of develop
   2. update version to .release
   3. test
   4. merge into release
   5. merge back into develop


