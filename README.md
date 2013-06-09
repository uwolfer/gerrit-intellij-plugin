gerrit-intellij-plugin
======================

Introduction
-----------

Unofficial [IntelliJ Platform] plugin for [Gerrit Code Review Tool].

[IntelliJ Platform]: http://www.jetbrains.com/idea/
[Gerrit Code Review Tool]: http://code.google.com/p/gerrit/


Building the plugin
------------------

To build the plugin on your machine you need to have at least a downloaded copy of IntelliJ.
The plugin depends on multiple jars of IntelliJ IDEA but as these are not available via Maven Central, you'll have to
install the various Intellij jars located in the lib folder of your IntelliJ install into your local Maven repository.

For your convienience there is a bash script which will do exactly this for you
```
    $ cd gerrit-intellij-plugin/
    $ ./install-intellij-libs.sh <IntelliJ Version> <Path to IntelliJ>
```

After you install all the jars this plugin needs into your local repo, just run

    mvn package

The resulting zip file will be located in the target folder.


Credits
------
* https://github.com/gshakhn/sonar-intellij-plugin/ for code examples (e.g. Maven integration, IntelliJ Plugin setup)
* IntelliJ Github plugin (some code of this plugin is based on its code)


Copyright and license
--------------------

Copyright 2013 Urs Wolfer

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this work except in compliance with the License.
You may obtain a copy of the License in the LICENSE file, or at:

  [http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
