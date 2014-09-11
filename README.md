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
4. Edit Owlkb.java and change the location of the ontology file
   (you can search for "Location of OWL file" to find the line in question)
5. Use "make" to invoke the Makefile and compile Owlkb.  Alternately, compile manually with "javac -g Owlkb.java".
6. Owlkb is now installed.  In order to run it, from the working directory chosen in step 2, type "java Owlkb".
   It is left up to the user to make it persistent (e.g. with crontab, etc.)
