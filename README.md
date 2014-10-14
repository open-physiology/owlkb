For more in-depth documentation: http://open-physiology.net/owlkb/doc.html

Owlkb 2.0 is a server that loads a knowledgebase, in the form of an OWL ontology,
and handles queries, in Manchester syntax, via an API.  In case a class does not already
exist that is equivalent to the query results, Owlkb will add a so-called "composite" term
to the ontology.  Owlkb is part of the RICORDO project.  Owlkb 1.0 was written by
Sarala Wimalaratne.  Owlkb 2.0 was rewritten by Sam Alexander.

INSTALLATION

1. Ensure a java compiler and runtime engine are installed.
2. "git clone" the project into whatever working directory you like.  Work in this directory for subsequent steps.
3. Use "jar -xf dep.jar" to expand Owlkb's dependencies.
4. Use "make" to invoke the Makefile and compile Owlkb.  Alternately, compile manually with "javac -g Owlkb.java".
5. Owlkb is now installed.  In order to run it, from the working directory chosen in step 2, type:
   "java Owlkb -file <path to ontology file>"
   Or, type "java Owlkb -help" for help on all the command-line options.

One way to test the install is to query via command line.
For example, if your ontology contains terms EXAMPLE_00015, EXAMPLE_00020, and relations inheres-in and part-of,
some example commandline queries are:

curl "http://localhost:20080/subterms/EXAMPLE_00015"

curl --header "Accept: application/json" "http://localhost:20080/subterms/EXAMPLE_00015"

curl "http://localhost:20080/terms/EXAMPLE_00015%20and%20inheres-in%20some%20EXAMPLE_00020"

curl "http://localhost:20080/eqterms/part-of%20some%20(inheres-in%20some%20EXAMPLE_00015)"

etc.

LICENSE

Copyright 2014 The Farr Institute

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
