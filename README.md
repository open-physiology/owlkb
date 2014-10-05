For more in-depth documentation: http://www.semitrivial.com/owlkb/doc.php

Owlkb is a server that loads a knowledgebase, in the form of an OWL ontology,
and handles queries, in Manchester syntax, via an API.  In case a class does not already
exist that is equivalent to the query results, Owlkb will add a so-called "composite" term
to the ontology.  Owlkb is part of the RICORDO project.  Owlkb was first written by
Sarala Wimalaratne.  This version of Owlkb is a version rewritten by Sam Alexander.

LICENSE

This is an unofficial initial release of Owlkb.
Please await further versions for a license.

INSTALLATION

1. Ensure a java compiler and runtime engine are installed.
2. "git clone" the project into whatever working directory you like.  Work in this directory for subsequent steps.
3. Use "jar -xf dep.jar" to expand Owlkb's dependencies.
4. Use "make" to invoke the Makefile and compile Owlkb.  Alternately, compile manually with "javac -g Owlkb.java".
5. Owlkb is now installed.  In order to run it, from the working directory chosen in step 2, type:
   "java Owlkb -file <path to ontology file>"
   Or, type "java Owlkb -help" for help on all the command-line options.

This repository doesn't include any GUI or anything.  One way to test the install is to query via command line.
For example, if your ontology contains terms EXAMPLE_00015, EXAMPLE_00020, and relations inheres-in and part-of,
some example commandline queries are:

curl "http://localhost:20080/subterms/EXAMPLE_00015"

curl --header "Accept: application/json" "http://localhost:20080/subterms/EXAMPLE_00015"

curl "http://localhost:20080/terms/EXAMPLE_00015%20and%20inheres-in%20some%20EXAMPLE_00020"

curl "http://localhost:20080/eqterms/part-of%20some%20(inheres-in%20some%20EXAMPLE_00015)"

etc.
