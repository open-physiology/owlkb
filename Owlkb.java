/*
 * Owlkb 2.0, part of RICORDO.
 * On the web: http://open-physiology.org
 * Programmed by Sarala Wimaralatne, reprogrammed by Sam Alexander.
 *
 * Copyright 2014 The Farr Institute
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.semanticweb.owlapi.io.UnparsableOntologyException;
import org.semanticweb.owlapi.io.OWLOntologyCreationIOException;
import org.coode.owlapi.manchesterowlsyntax.ManchesterOWLSyntaxEditorParser;
import org.semanticweb.owlapi.util.BidirectionalShortFormProvider;
import org.semanticweb.owlapi.util.BidirectionalShortFormProviderAdapter;
import org.semanticweb.owlapi.util.AnnotationValueShortFormProvider;
import org.semanticweb.owlapi.util.OWLOntologyImportsClosureSetProvider;
import org.semanticweb.owlapi.expression.OWLEntityChecker;
import org.semanticweb.owlapi.expression.ShortFormEntityChecker;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.Headers;

public class Owlkb
{
  /*
   * Variables to be specified by command-line argument.
   */
  public String reasonerName;      // Reasoner name.  Default: "elk"
  public boolean hdSave;   // Whether to save changes to harddrive.  Default: true
  public String uclSyntax; // Endpoint for UCL syntax server.  Default: null
  public String kbNs;       // Namespace for RICORDO_### terms.  Default: "http://www.ricordo.eu/ricordo.owl"
  public String kbFilename; // Name of ontology file.  Default: "/home/sarala/testkb/ricordo.owl"
  public boolean helpOnly; // Whether to show helpscreen and exit.  Default: false
  public int port;          // Port number to listen on.  Default: 20080
  public String sparql;     // URL of SPARQL endpoint
  public boolean getCountsFromFeather; // For easy reversion in case SPARQL doesn't work
  public String openPhactsAppId;  // For querying openPHACTS API
  public String openPhactsAppKey; // For querying openPHACTS API

  /*
   * Variables to be initialized elsewhere than the command-line
   */
  OWLDataFactory df;
  BidirectionalShortFormProvider shortformProvider;
  BidirectionalShortFormProviderAdapter annotProvider;
  OWLOntologyImportsClosureSetProvider ontSet;
  Set<OWLOntology> importClosure;
  OWLAnnotationProperty rdfsLabel;

  public static void main(String [] args) throws Exception
  {
    Owlkb owlkb = new Owlkb();
    owlkb.run(args);
  }

 /*
   * Load the ontology and launch a server on user-specified port (default 20080) to serve
   * the owlkb API, as described at http://www.semitrivial.com/owlkb/api.php
   */
  public void run(String [] args) throws Exception
  {
    initOwlkb(args);

    if ( helpOnly )
      return;

    /*
     * Load the main ontology
     */
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

    logString( "Loading ontology...");

    OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration();         // If the main ontology imports an RDF fragment,
    config = config.setMissingOntologyHeaderStrategy(OWLOntologyLoaderConfiguration.MissingOntologyHeaderStrategy.IMPORT_GRAPH);  // prevent that fragment from being saved into the ontology.

    File kbFile;
    OWLOntology ont;

    try
    {
      kbFile = new File(kbFilename);
    }
    catch ( NullPointerException e )
    {
      System.out.println( "Could not load file: filename is null" );
      System.out.println( "If you didn't already, try running Owlkb with command line arguments: -file <filename>" );
      return;
    }
    catch ( Exception e )
    {
      System.out.println( "An unknown error occurred while trying to parse/load filename: " + kbFilename );
      return;
    }

    ont = loadOwlkbOntology( kbFile, kbFilename, manager, config );

    if ( ont == null )
      return;

    logString( "Ontology is loaded.");

    IRI iri = manager.getOntologyDocumentIRI(ont);

    /*
     * Load the ontologies imported by the main ontology (e.g., the reference ontologies)
     */
    importClosure = ont.getImportsClosure();
    ontSet = new OWLOntologyImportsClosureSetProvider(manager, ont);

    /*
     * Establish infrastructure for converting long URLs to short IRIs and vice versa
     * (e.g., converting between "http://purl.org/obo/owlapi/quality#PATO_0000014" and "PATO_0000014")
     */
    shortformProvider = new BidirectionalShortFormProviderAdapter(manager, importClosure, new org.semanticweb.owlapi.util.SimpleShortFormProvider());
    OWLEntityChecker entityChecker = new ShortFormEntityChecker(shortformProvider);

    /*
     * Infrastructure for searching for classes by label
     */
    List<OWLAnnotationProperty> labeltypeList = new ArrayList<OWLAnnotationProperty>();
    labeltypeList.add(rdfsLabel);
    Map<OWLAnnotationProperty,List<String>> emptyMap = new HashMap<OWLAnnotationProperty,List<String>>();
    AnnotationValueShortFormProvider preAnnotProvider = new AnnotationValueShortFormProvider(labeltypeList, emptyMap, ontSet );
    annotProvider = new BidirectionalShortFormProviderAdapter(manager, importClosure, preAnnotProvider);

    /*
     * Initiate the reasoner
     */
    logString( "Establishing "+reasonerName+" reasoner...");

    OWLReasoner r;

    if ( reasonerName.equals("elk") )
    {
      OWLReasonerFactory rf = new ElkReasonerFactory();
      r = rf.createReasoner(ont);
    }
    else
      r = new org.semanticweb.HermiT.Reasoner(ont);  //Hermit reasoner

    logString( "Reasoner established.");

    /*
     * Precompute inferences.
     */
    logString( "Precomputing inferences...");

    long startTime = System.nanoTime();
    r.precomputeInferences(InferenceType.CLASS_HIERARCHY);

    logString( "Finished precomputing inferences (took "+(System.nanoTime()-startTime)/1000000+"ms)" );

    /*
     * Launch HTTP server
     */
    logString( "Initiating server...");

    HttpServer server = HttpServer.create(new java.net.InetSocketAddress(port), 0 );
    server.createContext("/subterms", new NetHandler("subterms", r, manager, ont, entityChecker, iri));
    server.createContext("/siblings", new NetHandler("siblings", r, manager, ont, entityChecker, iri));
    server.createContext("/parents", new NetHandler("parents", r, manager, ont, entityChecker, iri));
    server.createContext("/children", new NetHandler("children", r, manager, ont, entityChecker, iri));
    server.createContext("/subhierarchy", new NetHandler("subhierarchy", r, manager, ont, entityChecker, iri));
    server.createContext("/apinatomy", new NetHandler("apinatomy", r, manager, ont, entityChecker, iri));
    server.createContext("/eqterms", new NetHandler("eqterms", r, manager, ont, entityChecker, iri));
    server.createContext("/addlabel", new NetHandler("addlabel", r, manager, ont, entityChecker, iri));
    server.createContext("/terms", new NetHandler("terms", r, manager, ont, entityChecker, iri));
    server.createContext("/instances", new NetHandler("instances", r, manager, ont, entityChecker, iri));
    server.createContext("/labels", new NetHandler("labels", r, manager, ont, entityChecker, iri));
    server.createContext("/search", new NetHandler("search", r, manager, ont, entityChecker, iri));
    server.createContext("/rdfstore", new NetHandler("rdfstore", r, manager, ont, entityChecker, iri));
    server.createContext("/test", new NetHandler("test", r, manager, ont, entityChecker, iri));
    server.createContext("/shortestpath", new NetHandler("shortestpath", r, manager, ont, entityChecker, iri));
    server.createContext("/generate-triples", new NetHandler("generate-triples", r, manager, ont, entityChecker, iri));
    server.createContext("/subgraph", new NetHandler("subgraph", r, manager, ont, entityChecker, iri));
    server.createContext("/similar_molecules", new NetHandler("similar_molecules", r, manager, ont, entityChecker, iri));

    server.createContext("/gui", new NetHandler("gui", r, manager, ont, entityChecker, iri));

    server.setExecutor(null);
    server.start();

    logString( "Server initiated.");
  }

  class NetHandler implements com.sun.net.httpserver.HttpHandler
  {
    String srvType;
    OWLReasoner r;
    OWLOntologyManager m;
    OWLOntology o;
    OWLEntityChecker ec;
    IRI iri;

    public NetHandler(String srvType, OWLReasoner r, OWLOntologyManager m, OWLOntology o, OWLEntityChecker ec, IRI iri)
    {
      this.srvType = srvType;
      this.r = r;
      this.m = m;
      this.o = o;
      this.ec = ec;
      this.iri = iri;
    }

    public void handle(HttpExchange t) throws java.io.IOException
    {
      if ( srvType.equals("gui") )
      {
        sendGui(t);
        return;
      }

      Headers requestHeaders = t.getRequestHeaders();
      boolean fJson = ( requestHeaders.get("Accept") != null && requestHeaders.get("Accept").contains("application/json") );
      boolean verbose = false;
      boolean longURI = false;

      String response;

      String req = t.getRequestURI().toString().substring(2+srvType.length());

      Map<String,String> args;
      int qMark = req.indexOf("?");
      if ( qMark != -1 )
      {
        String rawArgs = req.substring(qMark+1);
        req = req.substring(0,qMark);
        args = getArgs( rawArgs );

        if ( !fJson && args.containsKey( "json" ) )
          fJson = true;

        if ( args.containsKey( "verbose" ) )
        {
          verbose = true;
          fJson = true;
        }

        if ( args.containsKey( "longURI" ) )
          longURI = true;
      }
      else
        args = new HashMap<String,String>();

      req = URLDecode(req);

      if ( checkForNonEL( req, t ) )
        return;

      logString( "Got request: ["+req+"]" );
      long startTime = System.nanoTime();

      if ( srvType.equals("labels") || srvType.equals("search") )
      {
        boolean isLabels = srvType.equals("labels");

        ArrayList<String> terms = (isLabels ? getLabels( req, o ) : SearchByLabel( req, o, verbose ));

        if ( terms == null || terms.isEmpty() )
          response = (isLabels ? "No class by that shortform." : "No class with that label.");
        else
          response = computeResponse( terms, fJson, false, verbose && !isLabels );
      }
      else
      if ( srvType.equals("addlabel") )
        response = computeAddlabelResponse( o, iri, m, req, fJson );
      else
      if ( srvType.equals("rdfstore") )
        response = computeRdfstoreResponse( o, iri, m, ec, r, req );
      else
      if ( srvType.equals("apinatomy") )
      {
        response = computeApinatomyResponse( o, iri, m, r, req );
        fJson = true;
      }
      else
      if ( srvType.equals("generate-triples") )
      {
        if ( t.getRemoteAddress().getAddress().isLoopbackAddress() )
          response = computeGenerateTriplesResponse( o, iri, m, r, req );
        else
          response = "{\"error\": \"Only requests originating from localhost can run generate-triples\"}";

        fJson = true;
      }
      else
      if ( srvType.equals("shortestpath") )
      {
        response = computeShortestpathResponse( o, iri, m, r, req );
        fJson = true;
      }
      else
      if ( srvType.equals("similar_molecules") )
      {
        response = computeSimilarMoleculesResponse( o, iri, m, r, ec, req );
        fJson = true;
      }
      else
      if ( srvType.equals("subgraph") )
      {
        response = computeSubgraphResponse( o, iri, m, r, req );
        fJson = true;
      }
      else
      try
      {
        OWLClassExpression exp;
        String manchesterError = "";

        if ( uclSyntax != null )
        {
          String lolsReply = queryURL( uclSyntax + URLEncode(req) );

          if ( lolsReply == null )
          {
            exp = parseManchester( req, o, ec );
            if ( exp == null )
              manchesterError = "Could not connect to LOLS for UCL syntax parsing";
          }
          else
          {
            String error = naiveJsonParse( lolsReply, "Error" );
            if ( error != null && !error.trim().equals("") )
            {
              manchesterError = error.trim();
              exp = null;
            }
            else
            {
              String ambigs = naiveJsonParse( lolsReply, "Ambiguities", "\n  [", "\n  ]" );
              if ( ambigs != null && !ambigs.trim().equals("") )
              {
                manchesterError = "{\n  \"Ambiguities\":\n  [\n    " + ambigs.trim() + "\n  ]\n}";
                exp = null;
              }
              else
              {
                String uclToManchester = naiveJsonParse( lolsReply, "Result" );
                if ( uclToManchester == null )
                {
                  manchesterError = lolsReply.trim();
                  exp = null;
                }
                else
                {
                  exp = parseManchester( uclToManchester, o, ec );
                  if ( exp == null )
                  {
                    String possibleError = naiveJsonParse( lolsReply, "Possible_error" );
                    if ( possibleError != null )
                      manchesterError = possibleError;
                  }
                }
              }
            }
          }
        }
        else
          exp = parseManchester( req, o, ec );

        if ( exp == null )
        {
          if ( !manchesterError.equals("") )
            response = manchesterError;
          else
            response = "Malformed Manchester query";
        }
        else
        {
          if ( srvType.equals("subterms")
          ||   srvType.equals("siblings")
          ||   srvType.equals("parents")
          ||   srvType.equals("children")
          ||   srvType.equals("eqterms")
          ||   srvType.equals("instances")
          ||   srvType.equals("terms") )
          {
            ArrayList<String> terms = null;

            if ( srvType.equals("subterms") )
              terms = getSubTerms(exp,r,false,false,verbose);
            else if ( srvType.equals("siblings") )
              terms = getSiblings(exp,r,false,false,verbose);
            else if ( srvType.equals("parents") )
              terms = getParents(exp,r,false,false,verbose);
            else if ( srvType.equals("children") )
              terms = getChildren(exp,r,false,false,verbose);
            else if ( srvType.equals("eqterms") )
              terms = addTerm(exp,r,m,o,iri,verbose );
            else if ( srvType.equals("instances") )
              terms = getInstances(exp,r,verbose);
            else if ( srvType.equals("terms") )
              terms = getTerms(exp,r,verbose);

            response = computeResponse( terms, fJson, longURI, verbose );
          }
          else if ( srvType.equals("subhierarchy") )
          {
            response = computeSubhierarchyResponse( exp, r );
          }
          else if ( srvType.equals("test") )
            response = computeDemoResponse( exp, r, m, o, iri, startTime, fJson, verbose );
          else
            response = "Unrecognized request";
        }
      }
      catch(Exception e)
      {
        response = "There was an error getting the results.";
      }

      String callback = args.get("callback"); // JSONP support
      if ( callback != null )
      {
        String jsonpHeader = "typeof "+callback+" === 'function' && "+callback+"(\n";
        response = jsonpHeader + response + ");";
      }

      logString( "Transmitting response..." );

      sendResponse( t, response, fJson );

      /*
       * Measure computation time in ms.
       */
      long runTime = (System.nanoTime() - startTime) / 1000000;
      logString( "It took "+runTime+"ms to handle the request." );
    }
  }

  public boolean checkForNonEL( String req, HttpExchange t )
  {
    /*
     * To do: improve this function, which is currently just a bandaid
     */
    try
    {
      String lower = req.toLowerCase();

      if ( lower.contains(" or ") )
      {
        sendResponse( t, "Disjunction ('or') is forbidden because it would make the ontology non-EL.", false );
        return true;
      }
      if ( lower.contains(" not ") || lower.substring(0,4).equals("not ") )
      {
        sendResponse( t, "Negation ('not') is forbidden because it would make the ontology non-EL.", false );
        return true;
      }

      return false;
    }
    catch ( Exception e )
    {
      return false;
    }
  }

  public void sendResponse( HttpExchange t, String response, boolean fJson ) throws java.io.IOException
  {
    Headers h = t.getResponseHeaders();
    h.add("Cache-Control", "no-cache, no-store, must-revalidate");
    h.add("Pragma", "no-cache");
    h.add("Expires", "0");

    if ( fJson )
      h.add("Content-Type", "application/json");

    t.sendResponseHeaders(200,response.getBytes().length);
    java.io.OutputStream os = t.getResponseBody();
    os.write(response.getBytes());
    os.close();

    logString( "Response transmitted.");
  }

  private ArrayList<String> getSubTerms(OWLClassExpression exp, OWLReasoner r, boolean longURI, boolean direct, boolean verbose )
  {
    ArrayList<String> idList = new ArrayList<String>();
    NodeSet<OWLClass> subClasses = r.getSubClasses(exp, direct);

    for ( Node<OWLClass> owlClassNode : subClasses )
      classToTermlist( owlClassNode, idList, longURI, verbose );

    return idList;
  }

  private ArrayList<String> getParents(OWLClassExpression exp, OWLReasoner r, boolean longURI, boolean direct, boolean verbose )
  {
    Set<Node<OWLClass>> parentNodes = r.getSuperClasses( exp, true ).getNodes();
    ArrayList<String> idList = new ArrayList<String>();

    for ( Node<OWLClass> n : parentNodes )
      classToTermlist( n, idList, longURI, verbose );

    return idList;
  }

  private ArrayList<String> getChildren(OWLClassExpression exp, OWLReasoner r, boolean longURI, boolean direct, boolean verbose )
  {
    Set<Node<OWLClass>> childNodes = r.getSubClasses( exp, true ).getNodes();
    ArrayList<String> idList = new ArrayList<String>();

    for ( Node<OWLClass> n : childNodes )
      classToTermlist( n, idList, longURI, verbose );

    return idList;
  }

  private ArrayList<String> getSiblings(OWLClassExpression exp, OWLReasoner r, boolean longURI, boolean direct, boolean verbose )
  {
    Set<Node<OWLClass>> parentNodes = r.getSuperClasses( exp, true ).getNodes();
    HashSet<String> sibs = new HashSet<String>();

    ArrayList<String> retVal;

    if ( verbose )
      retVal = new ArrayList<String>();
    else
      retVal = null;

    for ( Node<OWLClass> pnode : parentNodes )
    {
      OWLClass parent = pnode.getRepresentativeElement();
      NodeSet<OWLClass> childNodes = r.getSubClasses( parent, true );

      String parentLabel = null, pID = null;

      if ( verbose )
      {
        parentLabel = labelByClass(parent);

        if ( parentLabel == null )
          parentLabel = "null";

        pID = shortUrl(parent.getIRI().toString());
      }

      for ( OWLClass c : childNodes.getFlattened() )
      {
        String sibID = shortUrl(c.getIRI().toString());

        if ( sibs.contains( sibID ) )
          continue;

        sibs.add( sibID );

        if ( verbose )
        {
          String label = labelByClass(c);

          if ( label == null )
            label = "null";

          String jsObj = "{\n   \"sibling\":\""+sibID+"\",\n   \"label\":\""+label+"\",\n   \"parent\":\""+pID+"\",\n   \"parent_label\":\""+parentLabel+"\"\n}";
          retVal.add(jsObj);
        }
      }
    }

    if ( verbose )
      return retVal;
    else
      return new ArrayList<String>(sibs);
  }

  private ArrayList<String> getInstances(OWLClassExpression exp, OWLReasoner r, boolean verbose)
  {
    ArrayList<String> idList = new ArrayList<String>();
    NodeSet<OWLNamedIndividual> inst = r.getInstances(exp, false);

    for (Node<OWLNamedIndividual> ind : inst)
      individualToTermlist( ind, idList, false, verbose );

    return idList;
  }

  private ArrayList<String> getEquivalentTerms(OWLClassExpression exp, OWLReasoner r, boolean verbose)
  {
    ArrayList<String> idList = new ArrayList<String>();
    Node<OWLClass> equivalentClasses = r.getEquivalentClasses(exp);

    for ( OWLClass c : equivalentClasses.getEntities() )
      objToTermlist( c, idList, false, verbose );

    return idList;
  }

  public String labelByClass(OWLEntity c)
  {
    for ( OWLOntology imp : importClosure )
    {
      Set<OWLAnnotation> annots = c.getAnnotations( imp, rdfsLabel );

      for ( OWLAnnotation a : annots )
      {
        if ( a.getValue() instanceof OWLLiteral )
          return ((OWLLiteral)a.getValue()).getLiteral();
      }
    }

    return null;
  }

  public ArrayList<String> getLabels(String shortform, OWLOntology o )
  {
    OWLEntity e = shortformProvider.getEntity(shortform);

    if ( e == null )
      return null;

    ArrayList<String> idList = new ArrayList<String>();

    Set<OWLAnnotation> annots = e.getAnnotations(o, rdfsLabel );

    if ( annots.isEmpty() )
    {
      for ( OWLOntology imp : importClosure )
      {
        annots = e.getAnnotations( imp, rdfsLabel );
        if ( !annots.isEmpty() )
          break;
      }
    }

    if ( annots.isEmpty() )
      idList.add( "(Unlabeled class)" );   //To do: create "advanced commandline options" one of which chooses Queen's vs. American English
    else
    for ( OWLAnnotation a : annots )
    {
      if ( a.getValue() instanceof OWLLiteral )
        idList.add( ((OWLLiteral)a.getValue()).getLiteral() );
    }

    return idList;
  }

  public ArrayList<String> SearchByLabel(String label, OWLOntology o, boolean verbose )
  {
    Set<OWLEntity> ents = annotProvider.getEntities(label);

    if ( ents == null || ents.isEmpty() )
      return null;

    ArrayList<String> idList = new ArrayList<String>();

    for ( OWLEntity e : ents )
    {
      if ( e.isOWLClass() && !e.asOWLClass().isAnonymous() )
        objToTermlist( e, idList, false, verbose );
    }

    return idList;
  }

  public ArrayList<String> getTerms(OWLClassExpression exp, OWLReasoner r, boolean verbose)
  {
    ArrayList<String> idList = new ArrayList<String>();

    idList.addAll(getEquivalentTerms(exp,r,verbose));
    idList.addAll(getSubTerms(exp,r,false,false,verbose));

    return idList;
  }

  public ArrayList<String> addTerm(OWLClassExpression exp, OWLReasoner r, OWLOntologyManager mgr, OWLOntology ont, IRI iri, boolean verbose )
  {
    ArrayList<String> idList = getEquivalentTerms(exp,r,verbose);
    if(idList.isEmpty())
    {
      String ricordoID = String.valueOf(System.currentTimeMillis());
      OWLClass newOwlClass = df.getOWLClass(IRI.create(kbNs + ricordoID));

      mgr.addAxiom(ont, df.getOWLEquivalentClassesAxiom(newOwlClass, exp) );

      if ( reasonerName.equals("elk") )
        r.flush();

      maybeSaveOntology( ont, iri, mgr );

      objToTermlist( newOwlClass, idList, false, verbose );
      r.precomputeInferences(InferenceType.CLASS_HIERARCHY);
    }

    return idList;
  }

  public static String shortUrl(String url)
  {
    int i = url.lastIndexOf("#");

    if ( i != -1 )
      return url.substring(i+1);

    i = url.lastIndexOf("/");
    int j = url.indexOf("/");

    if ( i != j )
      return url.substring(i+1);

    return url;
  }

  /*
   * Basic logging to stdout
   */
  public static void logString( String x )
  {
    System.out.println( x );
  }

  public String computeResponse( ArrayList<String> terms, boolean fJson, boolean longURI, boolean verbose )
  {
    StringBuilder x = new StringBuilder();

    if ( verbose )
    {
      boolean fFirst = false;
      x.append("{\n \"results\":\n [\n  " );

      for ( String js : terms )
      {
        if ( !fFirst )
          fFirst = true;
        else
          x.append( ",\n  " );

        x.append( js );
      }

      x.append("\n ]\n}");

      return x.toString();
    }

    if ( fJson )
    {
      x.append("[\n ");
      boolean fFirst = false;

      for ( String termp : terms )
      {
        if ( !fFirst )
          fFirst = true;
        else
          x.append(",\n ");

        x.append( "\"" + (longURI ? termp : shortUrl(termp)) +"\"" );
      }
      x.append("]");
    }
    else
    {
      x.append("<table><tr><th>ID</th></tr>");

      for ( String termp : terms )
        x.append("<tr><td>" + (longURI ? termp : shortUrl(termp)) +"</td></tr>");

      x.append("</table>");
    }

    return x.toString();
  }

  public void initOwlkb( String [] args )
  {
    df = OWLManager.getOWLDataFactory();
    rdfsLabel = df.getRDFSLabel();

    parseCommandlineArguments(args);
  }

  public void parseCommandlineArguments( String [] args )
  {
    reasonerName = "elk";
    hdSave = true;
    kbNs = "http://www.ricordo.eu/ricordo.owl#RICORDO_";
    kbFilename = "/home/sarala/testkb/ricordo.owl";  // Keep this silly default for backward compatibility
    sparql = null;
    helpOnly = false;
    port = 20080;
    getCountsFromFeather = false;
    openPhactsAppId = null;
    openPhactsAppKey = null;

    int i;
    String flag;

    for ( i = 0; i < args.length; i++ )
    {
      if ( args[i].length() > 2 && args[i].substring(0,2).equals("--") )
        flag = args[i].substring(2).toLowerCase();
      else if ( args[i].length() > 1 && args[i].substring(0,1).equals("-") )
        flag = args[i].substring(1).toLowerCase();
      else
        flag = args[i].toLowerCase();

      if ( flag.equals("help") || flag.equals("h") )
      {
        System.out.println( "Command line options are as follows:"                  );
        System.out.println( "------------------------------------"                  );
        System.out.println( "-file <path to file>"                                  );
        System.out.println( "(Specifies which ontology file to use)"                );
        System.out.println( "------------------------------------"                  );
        System.out.println( "-port <number>"                                        );
        System.out.println( "(Specified which port the server runs on)"             );
        System.out.println( "(Default: 20080)"                                      );
        System.out.println( "------------------------------------"                  );
        System.out.println( "-reasoner elk, or -reasoner hermit"                    );
        System.out.println( "(Specifies which reasoner to use)"                     );
        System.out.println( "(Default: elk)"                                        );
        System.out.println( "------------------------------------"                  );
        System.out.println( "-namespace <iri>"                                      );
        System.out.println( "(Specifies namespace for ontology)"                    );
        System.out.println( "(Default: http://www.ricordo.eu/ricordo.owl#RICORDO_)" );
        System.out.println( "------------------------------------"                  );
        System.out.println( "-save true, or -save false"                            );
        System.out.println( "(Specifies whether owlfile changes are saved)"         );
        System.out.println( "(Default: true)"                                       );
        System.out.println( "------------------------------------"                  );
        System.out.println( "-uclsyntax true, or -uclsyntax false"                  );
        System.out.println( "(Specifies whether OWLKB understands UCL syntax)"      );
        System.out.println( "(Default: false)"                                      );
        System.out.println( "------------------------------------"                  );
        System.out.println( "-sparql <url base>"                                    );
        System.out.println( "(Base of URL to use as SPARQL endpoint, to allow"      );
        System.out.println( " interaction with a triple store.)"                    );
        System.out.println( "(Default: null)"                                       );
        System.out.println( "------------------------------------"                  );
/*
        System.out.println( "-openPHACTSid <ID for openPHACTS API>"                 );
        System.out.println( "-openPHACTSkey <App key for openPHACTS API>"           );
        System.out.println( "(For enabling Owlkb to query the openPHACTS"           );
        System.out.println( " API.)"                                                );
        System.out.println( "------------------------------------"                  );
*/
        System.out.println( "-help"                                                 );
        System.out.println( "(Displays this helpfile)"                              );
        System.out.println( "" );
        helpOnly = true;
        return;
      }

      if ( flag.equals("port") || flag.equals("p") )
      {
        if ( i+1 < args.length )
        {
          try
          {
            port = Integer.parseInt(args[i+1]);
          }
          catch( Exception e )
          {
            System.out.println( "Port must be a number." );
            helpOnly = true;
            return;
          }
          System.out.println( "Owlkb will listen on port "+args[++i] );
        }
        else
        {
          System.out.println( "Which port do you want the server to listen on?" );
          helpOnly = true;
          return;
        }
      }
      else if ( flag.equals("rname") || flag.equals("reasoner") )
      {
        if ( i+1 < args.length && (args[i+1].equals("elk") || args[i+1].equals("hermit")) )
        {
          reasonerName = args[++i];
          System.out.println( "Using "+reasonerName+" as reasoner" );
        }
        else
        {
          System.out.println( "Valid reasoners are: ELK, HermiT" );
          helpOnly = true;
          return;
        }
      }
      else if ( flag.equals("hd") || flag.equals("hd_save") || flag.equals("save") )
      {
        if ( i+1 < args.length && (args[i+1].equals("t") || args[i+1].equals("true")) )
          hdSave = true;
        else if ( i+1 < args.length && (args[i+1].equals("f") || args[i+1].equals("false")) )
        {
          hdSave = false;
          System.out.println( "Saving changes to hard drive: disabled." );
        }
        else
        {
          System.out.println( "hd_save can be set to: true, false" );
          helpOnly = true;
          return;
        }
        i++;
      }
      else if ( flag.equals("openphactsid") )
      {
        if ( i+1 < args.length )
        {
          openPhactsAppId = args[i+1];
          System.out.println( "Set to use "+args[i+1]+" as openPHACTS Application ID" );
        }
        i++;
      }
      else if ( flag.equals("openphactskey") )
      {
        if ( i+1 < args.length )
        {
          openPhactsAppKey = args[i+1];
          System.out.println( "Set to use "+args[i+1]+" as openPHACTS Application Key" );
        }
        i++;
      }
      else if ( flag.equals( "uclsyntax" ) || flag.equals("ucl-syntax") || flag.equals("ucl_syntax") )
      {
        if ( i+1 < args.length && (args[i+1].equals("t") || args[i+1].equals("true")) )
        {
          /*
           * Backwards compatibility (originally uclsyntax was a boolean and open-physiology was hardcoded as the endpoint
           */
          uclSyntax = "http://open-physiology.org:5052/uclsyntax/";
          System.out.println( "UCL Syntax: endpoint set to http://open-physiology.org:5052/uclsyntax/" );
        }
        else if ( i+1 < args.length && (args[i+1].equals("f") || args[i+1].equals("false")) )
          uclSyntax = null;
        else if ( i+1 < args.length )
        {
          uclSyntax = args[i+1];
          System.out.println( "UCL Syntax: endpoint set to " + uclSyntax );
        }
        else
        {
          System.out.println( "uclsyntax can be set to: true, false" );
          helpOnly = true;
          return;
        }
        i++;
      }
      else if (flag.equals("kbns") || flag.equals("ns") || flag.equals("namespace") )
      {
        if ( i+1 < args.length )
        {
          System.out.println( "Using "+args[i+1]+" as ontology namespace." );
          kbNs = args[++i];
        }
        else
        {
          System.out.println( "What do you want the ontology's namespace to be?" );
          System.out.println( "Default: http://www.ricordo.eu/ricordo.owl#RICORDO_" );
          helpOnly = true;
          return;
        }
      }
      else if ( flag.equals("sparql") )
      {
        if ( i+1 < args.length )
        {
          System.out.println( "Using "+args[i+1]+" as base of URL of SPARQL endpoint." );
          sparql = args[++i];
        }
        else
        {
          System.out.println( "Specify the base of the URL of the SPARQL endpoint you want to use." );
          System.out.println( "Default: null (in which case OWLKB will not interact with SPARQL)" );
          helpOnly = true;
          return;
        }
      }
      else if ( flag.equals("kbfilename") || flag.equals("filename") || flag.equals("kbfile") || flag.equals("file") || flag.equals("kb") )
      {
        if ( i+1 < args.length )
        {
          System.out.println( "Using "+args[i+1]+" as ontology filename." );
          kbFilename = args[++i];
        }
        else
        {
          System.out.println( "Specify the filename of the ontology." );
          helpOnly = true;
          return;
        }
      }
      else
      {
        System.out.println( "Unrecognized command line argument: "+args[i] );
        System.out.println( "For help, run with HELP as command line argument." );
        helpOnly = true;
        return;
      }
    }
  }

  public void sendGui(HttpExchange t)
  {
    String theHtml, theJS;

    try
    {
      theHtml = readFile("gui.html");
      theJS = readFile("gui.js");

      if ( theHtml == null || theJS == null )
      {
        sendResponse( t, "The GUI could not be sent, due to a problem with the html file or the javascript file.", false );
        return;
      }

      theHtml = theHtml.replace("@JAVASCRIPT", "<script type='text/javascript'>"+theJS+"</script>");

      sendResponse( t, theHtml, false );
    }
    catch(Exception e)
    {
      ;
    }
  }

  public String computeAddlabelResponse( OWLOntology o, IRI ontology_iri, OWLOntologyManager m, String req, boolean fJson )
  {
    int eqPos = req.indexOf('=');

    if ( eqPos == -1 || eqPos == 0 )
    {
      if ( fJson )
        return "{'syntax error'}";
      else
        return "Invalid syntax.  Syntax: /addlabel/iri=label, e.g.: /addlabel/RICORDO_123=volume of blood";
    }

    String iri = req.substring(0,eqPos);
    String label = req.substring(eqPos+1);

    if ( iri.length() < "RICORDO_".length() || !iri.substring(0,"RICORDO_".length()).equals("RICORDO_") )
    {
      if ( fJson )
        return "{'non-ricordo class error'}";
      else
        return "Only RICORDO classes can have labels added to them through OWLKB.";
    }

    if ( label.trim().equals("") )
    {
      if ( fJson )
        return "{'blank label error'}";
      else
        return "Blank labels are not allowed.";
    }

    OWLEntity e = shortformProvider.getEntity(iri);

    if ( e == null || !e.isOWLClass() )
    {
      if ( fJson )
        return "{'class not found error'}";
      else
        return "The specified class could not be found.  Please make sure you're using the shortform of the iri, e.g., RICORDO_123 instead of http://website.com/RICORDO_123";
    }

    Set<OWLAnnotation> annots = e.getAnnotations(o, rdfsLabel );

    if ( !annots.isEmpty() )
    {
      for ( OWLAnnotation a : annots )
      {
        if ( a.getValue() instanceof OWLLiteral )
        {
          if ( ((OWLLiteral)a.getValue()).getLiteral().equals(label) )
            return fJson ? "{'ok'}" : "Class "+iri+" now has label "+escapeHTML(label);
        }
      }
    }

    IRI rdfsLabelIRI = org.semanticweb.owlapi.vocab.OWLRDFVocabulary.RDFS_LABEL.getIRI();
    OWLAnnotation a = df.getOWLAnnotation( df.getOWLAnnotationProperty(rdfsLabelIRI), df.getOWLLiteral(label) );
    OWLAxiom axiom = df.getOWLAnnotationAssertionAxiom(e.asOWLClass().getIRI(), a);
    m.applyChange(new AddAxiom( o, axiom ));
    logString( "Added rdfs:label "+label+" to class "+iri+"." );

    maybeSaveOntology( o, ontology_iri, m );

    return fJson ? "{'ok'}" : "Class "+iri+" now has label "+escapeHTML(label);
  }

  public void maybeSaveOntology( OWLOntology ont, IRI iri, OWLOntologyManager m )
  {
    if ( hdSave )
    {
      logString( "Saving ontology to hard drive..." );

      try
      {
        m.saveOntology(ont,iri);
      }
      catch ( OWLOntologyStorageException e )
      {
        e.printStackTrace(); //To do: proper error handling here
      }

      logString( "Finished saving ontology to hard drive." );
    }
    else
      logString( "Skipping writing to hard drive (disabled by commandline argument)." );
  }

  public OWLClassExpression parseManchester( String manchester, OWLOntology o, OWLEntityChecker ec )
  {
    ManchesterOWLSyntaxEditorParser parser;
    OWLClassExpression exp;

    parser = new ManchesterOWLSyntaxEditorParser(df, manchester);
    parser.setDefaultOntology(o);
    parser.setOWLEntityChecker(ec);

    try
    {
      exp = parser.parseClassExpression();
    }
    catch(Exception e)
    {
      return null;
    }

    return exp;
  }

  public String computeRdfstoreResponse( OWLOntology o, IRI iri, OWLOntologyManager m, OWLEntityChecker ec, OWLReasoner r, String req )
  {
    String x = fullIriFromFullOrShortIri( req, o );

    if ( x != null )
      return x;

    OWLClassExpression exp = parseManchester( req, o, ec );

    if ( exp != null )
    {
      ArrayList<String> terms = getSubTerms(exp, r, true, false, false);

      return computeResponse( terms, true, true, false );
    }

    return req;
  }

  public String computeSimilarMoleculesResponse( OWLOntology o, IRI iri, OWLOntologyManager m, OWLReasoner reasoner, OWLEntityChecker ec, String req )
  {
    if ( openPhactsAppId == null || openPhactsAppKey == null )
      return "{\"error\": \"To use the similar_molecules command, OWLKB must have openPHACTS appID and appKey specified (using Owlkb's command-line arguments)\"}";

    if ( !req.substring(0,4).equals("http") )
      req = "http://rdf.ebi.ac.uk/resource/chembl/molecule/" + req;

    StringBuilder sb = new StringBuilder("https://beta.openphacts.org/1.5/compound/classifications?uri=");
    sb.append( URLEncode( req ) );
    sb.append( "&_format=json&app_id=" );
    sb.append( openPhactsAppId );
    sb.append( "&app_key=" );
    sb.append( openPhactsAppKey );

    String raw = queryURL( sb.toString() );

    if ( raw == null )
      return "{\"error\": \"Could not get details about indicated molecule from OpenPHACTS\"}";

    String classification = naiveJsonParse( raw, "hasChebiClassification", "[", "]" );

    if ( raw == null )
      return "{\"error\": \"Could not parse OpenPHACTS's response\"}";

    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("CHEBI_[\\d]*").matcher(classification);

    Set<OWLClass> parents = new HashSet<OWLClass>();

    while ( matcher.find() )
    {
      String chebi = matcher.group();
      OWLClassExpression exp = parseManchester( chebi, o, ec );

      if ( exp == null )
        return "{\"error\": \"OpenPHACTS indicated a CHEBI term, "+chebi+", unrecognized by OWLKB\"}";

      parents.addAll( reasoner.getSuperClasses( exp, true ).getFlattened() );
    }

    Set<OWLClass> siblings = new HashSet<OWLClass>();

    for ( OWLClass parent : parents )
      siblings.addAll( reasoner.getSubClasses( parent, true ).getFlattened() );

    return "{\"error\": \"This command is currently under construction\"}";
  }

  public String computeSubgraphResponse( OWLOntology o, IRI iri, OWLOntologyManager m, OWLReasoner reasoner, String req )
  {
    req = "," + req.replace("fma:", "http://purl.org/obo/owlapi/fma%23FMA_");

    String featherResponse = queryFeather("subgraph", req);

    if ( featherResponse == null )
      return "?";
    else
      return featherResponse;
  }

  public String computeShortestpathResponse( OWLOntology o, IRI iri, OWLOntologyManager m, OWLReasoner reasoner, String req )
  {
    req = req.replace("fma:", "http://purl.org/obo/owlapi/fma%23FMA_");
    String featherResponse = queryFeather("shortpath", req);

    if ( featherResponse == null )
      return "?";
    else
      return featherResponse;
  }

  public String computeGenerateTriplesResponse( OWLOntology o, IRI iri, OWLOntologyManager m, OWLReasoner reasoner, String req )
  {
    OWLReasoner r = reasoner;
    java.io.PrintWriter writer;

    try
    {
      writer = new java.io.PrintWriter( "triples.nt", "UTF-8" );
    }
    catch( Exception e )
    {
      return "{ \"error\": \"Could not open triples.nt for writing\" }";
    }

    for ( OWLOntology ont : importClosure )
    {
      Set<OWLClass> classes = ont.getClassesInSignature();

      for ( OWLClass c : classes )
      {
        NodeSet<OWLClass> subClasses = r.getSubClasses(c, true);
        String cString = c.toStringID();

        Set<OWLIndividual> inds = c.getIndividuals(ont);

        for ( OWLIndividual ind : inds )
        {
          if ( !(ind instanceof OWLNamedIndividual) )
            continue;

          String iString = ind.asOWLNamedIndividual().getIRI().toString();

          if ( iString.equals("") )
            continue;

          writer.println( "<" + iString + "> <http://open-physiology.org/#super-or-equal> <" + iString + "> ." );

          if ( !cString.equals("") )
            writer.println( "<" + cString + "> <http://open-physiology.org/#super-or-equal> <" + iString + "> ." );
        }

        if ( cString.equals("") )
          continue;

        writer.println( "<" + cString + "> <http://open-physiology.org/#super-or-equal> <" + cString + "> ." );

        for ( Node<OWLClass> subnode : subClasses )
        {
          OWLClass sub = subnode.getEntities().iterator().next();

          if ( sub.isOWLNothing() )
            continue;

          writer.println( "<" + cString + "> <http://open-physiology.org/#super-or-equal> <" + sub.toStringID() + "> ." );
        }

        Set<OWLClassExpression> supers = c.getSuperClasses(ont);
        for ( OWLClassExpression exp : supers )
        {
          if ( !(exp instanceof OWLObjectSomeValuesFrom) )
            continue;

          OWLRestriction restrict = (OWLRestriction) exp;

          Set<OWLObjectProperty> objProperties = restrict.getObjectPropertiesInSignature();
          boolean fBad = false;

          for ( OWLObjectProperty objPropery : objProperties )
          {
            if ( !objPropery.toStringID().equals( "http://purl.org/obo/owlapi/fma#regional_part_of" )
            &&   !objPropery.toStringID().equals( "http://purl.org/obo/owlapi/fma#constitutional_part_of" ) )
            {
              fBad = true;
              break;
            }
          }
          if ( fBad )
            continue;

          Set<OWLClass> classesInSignature = restrict.getClassesInSignature();

          for ( OWLClass classInSignature : classesInSignature )
          {
            writer.println( "<" + classInSignature.toStringID() + "> <http://open-physiology.org/#super-or-equal> <" + cString + "> ." );
            break;
          }
        }
      }
    }

    writer.close();

    return "{ \"result\": \"Triples saved to file triples.nt in owlkb directory\" }";
  }

  public String computeSubhierarchyResponse( OWLClassExpression exp, OWLReasoner r )
  {
    StringBuilder sb = new StringBuilder();

    sb.append( "{\n" );
    appendSubhierarchy( sb, exp, r, 1 );
    sb.append( "\n}" );

    return sb.toString();
  }

  public void appendSubhierarchy( StringBuilder sb, OWLClassExpression exp, OWLReasoner r, int indent )
  {
    appendSpaces( sb, indent );
    sb.append( "\"subterms\":\n" );
    appendSpaces( sb, indent );
    sb.append( "[\n" );

    Set<Node<OWLClass>> nodes = r.getSubClasses( exp, true ).getNodes();
    boolean isFirst = true;

    for ( Node<OWLClass> node : nodes )
    {
      OWLClass c = node.getRepresentativeElement();

      if ( c.isOWLNothing() )
        continue;

      if ( isFirst )
        isFirst = false;
      else
        sb.append( ",\n" );

      appendSpaces( sb, indent + 1 );
      sb.append( "{\n" );
      appendSpaces( sb, indent + 2 );
      sb.append( "\"term\": \"" + shortUrl(c.getIRI().toString()) + "\",\n" );

      String label = labelByClass( c );
      if ( label != null )
      {
        appendSpaces( sb, indent + 2 );
        sb.append( "\"label\": \"" + escapeJSON(label) + "\",\n" );
      }

      appendSubhierarchy( sb, c, r, indent+2 );

      sb.append( "\n" );
      appendSpaces( sb, indent + 1 );
      sb.append( "}" );
    }

    sb.append( "\n" );
    appendSpaces( sb, indent );
    sb.append( "]" );
  }

  public void appendSpaces( StringBuilder sb, int n )
  {
    sb.append( String.format( "%"+n+"s", "" ) );
  }

  public String computeApinatomyResponse( OWLOntology o, IRI iri, OWLOntologyManager m, OWLReasoner reasoner, String req )
  {
    String response = "[\n";
    boolean isFirstResult = true;

    req = req.replace("fma:", "FMA_");

    List<String> shortforms = java.util.Arrays.asList(req.split(","));

    /*
     * Max size chosen based on FMA's most prolific class, FMA_21792 ("Fascia of muscle"), which has 222 subs
     */
    if ( shortforms.size() > 250 )
      return "[]";

    if ( req.substring(0,6).equals( "24tile" ) )
    {
      String top24 = readFile("24tiles.dat");

      return (top24 != null) ? top24 : "[]";
    }

    if ( req.equals("pkpd_base") )
    {
      String topTiles = readFile("pkpdroot.dat");

      return (topTiles != null) ? topTiles : "[]";
    }

    for ( String shortform : shortforms )
    {
      OWLEntity e = shortformProvider.getEntity(shortform);

      if ( e == null || (!e.isOWLClass() && !e.isOWLNamedIndividual() ) )
        continue;

      if ( isFirstResult )
        isFirstResult = false;
      else
        response += ",\n";

      response += "  {\n    \"_id\": \"" + escapeJSON(shortform) + "\",\n";

      String theLabel = getOneRdfsLabel( e, o );

      if ( theLabel == null )
        theLabel = shortform;

      response += "    \"name\": \"" + escapeJSON(theLabel) + "\",\n    \"sub\":\n    [\n";

      if ( e.isOWLClass() )
      {
        List<ApinatomySub> subs = getApinatomySubs(e, reasoner, o);
        boolean isFirstSub = true;

        for ( ApinatomySub sub : subs )
        {
          if ( isFirstSub )
            isFirstSub = false;
          else
            response += ",\n";

          response += "      {\n        \"type\": \"" + escapeJSON(sub.type) + "\",\n";
          response += "        \"entity\":\n        {\n          \"_id\": \"" + escapeJSON(sub.id) + "\"\n        }\n      }";
        }
      }

      response += "\n    ]\n  }";
    }

    response = response.replace( "FMA_", "fma:" );

    response += "\n]";

    return response;
  }

  public String queryURL(String urlString)
  {
    StringBuilder buf = null;
    java.io.Reader r = null;

    try
    {
      java.net.URL url = new java.net.URL(urlString);
      java.net.URLConnection con = url.openConnection();

      /*
       * To do: Make these values configurable
       */
      con.setConnectTimeout( 1000 );
      con.setReadTimeout( 1000 );

      r = new java.io.InputStreamReader(con.getInputStream(), "UTF-8");
      buf = new StringBuilder();

      while (true)
      {
        int ch = r.read();
        if (ch < 0)
          break;
        buf.append((char) ch);
      }
      return buf.toString();
    }
    catch(Exception e)
    {
      return null;
    }
  }

  public String queryFeather( String command, String x )
  {
    return queryURL("http://open-physiology.org:5053/"+command+"/"+x);
  }

  public List<ApinatomySub> getApinatomySubs( OWLEntity e, OWLReasoner r, OWLOntology o )
  {
    List<ApinatomySub> response = new ArrayList<ApinatomySub>();

    if ( !( e instanceof OWLClass ) )
      return response;

    OWLClass c = e.asOWLClass();

    ArrayList<String> subclassList = getSubTerms(c, r, false, true, false);

    for ( String subclass : subclassList )
    {
      String theID = shortUrl( subclass );

      if ( !theID.equals( "Nothing" ) )
        response.add( new ApinatomySub( theID, "subclass" ) );
    }

    for ( OWLOntology imp : importClosure )
    {
      Set<OWLClassExpression> supers = c.getSuperClasses(imp);

      for ( OWLClassExpression exp : supers )
      {
        if ( !( exp instanceof OWLObjectSomeValuesFrom ) )
          continue;

        String type = null;
        OWLRestriction restrict = (OWLRestriction) exp;
        Set<OWLObjectProperty> objProperties = restrict.getObjectPropertiesInSignature();

        for ( OWLObjectProperty objPropery : objProperties )
        {
          String objPropertyShort = shortUrl( objPropery.toStringID() );

          if ( objPropertyShort.equals( "regional_part" ) )
            type = "regional part";
          else
          if ( objPropertyShort.equals( "constitutional_part" ) )
            type = "constitutional part";

          break;
        }

        if ( type == null )
          continue;

        Set<OWLClass> classesInSignature = restrict.getClassesInSignature();
        for ( OWLClass classInSignature : classesInSignature )
        {
          response.add( new ApinatomySub( shortUrl(classInSignature.toStringID()), type ) );
          break;
        }
      }

      Set<OWLIndividual> inds = c.getIndividuals(imp);

      for ( OWLIndividual ind : inds )
      {
        if ( !(ind instanceof OWLNamedIndividual) )
          continue;

        response.add( new ApinatomySub( shortUrl( ind.asOWLNamedIndividual().getIRI().toString() ), "subclass" ) );
      }
    }

    return response;
  }

  class ApinatomySub
  {
    public String id;
    public String type;

    public ApinatomySub(String id, String type)
    {
      this.id = id;
      this.type = type;
    }
  }

  public String getOneRdfsLabel( OWLEntity e, OWLOntology o )
  {
    Set<OWLAnnotation> annots = e.getAnnotations(o, rdfsLabel);

    if ( annots.isEmpty() )
    {
      for ( OWLOntology imp : importClosure )
      {
        annots = e.getAnnotations( imp, rdfsLabel );
        if ( !annots.isEmpty() )
          break;
      }
    }

    if ( annots.isEmpty() )
      return null;

    for ( OWLAnnotation a : annots )
    {
      if ( a.getValue() instanceof OWLLiteral )
        return ((OWLLiteral)a.getValue()).getLiteral();
    }

    return null;
  }

  public static String escapeJSON(String s)
  {
    int len = s.length();
    StringBuilder sb = new StringBuilder(len);

    for ( int i = 0; i < len; i++ )
    {
      switch( s.charAt(i) )
      {
        case '\\':
          sb.append( "\\\\" );
          break;
        case '\"':
          sb.append( "\\\"" );
          break;
        default:
          sb.append( s.charAt(i) );
          break;
      }
    }

    return sb.toString();
  }

  /*
   * escapeHTML taken from Bruno Eberhard at stackoverflow
   */
  public static String escapeHTML(String s)
  {
    StringBuilder out = new StringBuilder(Math.max(16, s.length()));

    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);

      if (c > 127 || c == '"' || c == '<' || c == '>' || c == '&')
      {
        out.append("&#");
        out.append((int) c);
        out.append(';');
      }
      else
        out.append(c);
    }
    return out.toString();
  }

  String fullIriFromFullOrShortIri( String x, OWLOntology o )
  {
    OWLEntity e = shortformProvider.getEntity(x);

    if ( e != null )
      return e.getIRI().toString();

    if ( o.containsClassInSignature(IRI.create(x)) )
      return x;

    return null;
  }

  public static Map<String, String> getArgs(String query)
  {
    Map<String, String> result = new HashMap<String, String>();

    for (String param : query.split("&"))
    {
      String pair[] = param.split("=");
      if (pair.length > 1)
        result.put(URLDecode(pair[0]), URLDecode(pair[1]));
      else
        result.put(URLDecode(pair[0]), "");
    }

    return result;
  }

  public String naiveJsonParse(String json, String key)
  {
    return naiveJsonParse( json, key, "\"", "\"" );
  }

  public String naiveJsonParse(String json, String key, String start, String end )
  {
    String needle = "\"" + key + "\":";
    int pos = json.indexOf(needle);

    if ( pos == -1 )
      return null;

    pos += needle.length();

    pos = json.indexOf(start, pos);

    if ( pos == -1 )
      return null;

    pos += start.length();

    int endPos = json.indexOf(end, pos);

    if ( endPos == -1 )
      return null;

    return json.substring(pos,endPos);
  }

  private void classToTermlist( Node<OWLClass> node, List<String> L, boolean longIRI, boolean verbose )
  {
    objToTermlist( node.getRepresentativeElement(), L, longIRI, verbose );
  }

  private void individualToTermlist( Node<OWLNamedIndividual> node, List<String> L, boolean longIRI, boolean verbose )
  {
    objToTermlist( node.getRepresentativeElement(), L, longIRI, verbose );
  }

  private void objToTermlist( OWLEntity c, List<String> L, boolean longIRI, boolean verbose )
  {
    String theIRI = longIRI ? c.getIRI().toString() : c.toStringID();

    if ( verbose )
    {
      StringBuilder sb = new StringBuilder();

      sb.append( "{\n \"term\": \"" );
      sb.append( theIRI );
      sb.append( "\",\n \"label\": " );

      String label = labelByClass(c);

      if ( label == null )
        sb.append( "null" );
      else
        sb.append( "\"" + escapeJSON(label) + "\"" );

      sb.append( "\n}" );

      L.add(sb.toString());
    }
    else
      L.add(theIRI);
  }

  String computeDemoResponse( OWLClassExpression exp, OWLReasoner r, OWLOntologyManager m, OWLOntology o, IRI iri, long startTime, boolean fJson, boolean verbose )
  {
    ArrayList<String> terms;

    try
    {
      terms = addTerm( exp, r, m, o, iri, verbose );
    }
    catch( Exception e )
    {
      return "Malformed Manchester query";
    }

    StringBuilder sb = new StringBuilder();

    if ( fJson )
    {
      sb.append( "{\"terms\": [" );

      if ( verbose )
        sb.append( jsObjsToCSV( terms ) );
      else
        sb.append( termsToCSV( terms ) );

      sb.append( "]," );
    }
    else
    {
      sb.append( "<h3>Term</h3><ul>" );

      for ( String termp : terms )
        sb.append( "<li>" + shortUrl(termp) + "</li>" );

      sb.append( "</ul>" );
    }

    ArrayList<String> subs;

    try
    {
      subs = getSubTerms(exp,r,false,false,verbose);
    }
    catch( Exception e )
    {
      return "There was an error getting the results";
    }

    if ( fJson )
    {
      sb.append( "\"subterms\": [" );

      if ( verbose )
        sb.append( jsObjsToCSV( subs ) );
      else
        sb.append( termsToCSV( subs ) );

      sb.append( "]," );
    }
    else if ( subs.size() != 0 )
    {
      sb.append( "<h3>Subterms</h3><ul>" );

      for ( String termp : subs )
        sb.append( "<li>" + shortUrl(termp) + "</li>" );

      sb.append( "</ul>" );
    }

    if ( fJson )
      sb.append( "\"runtime\": \"" + (System.nanoTime() - startTime) / 1000000 + "ms\"}" );
    else
      sb.append( "<h5>Runtime</h5>This computation took "+(System.nanoTime() - startTime) / 1000000+"ms to complete" );

    return sb.toString();
  }

  String termsToCSV( List<String> terms )
  {
    StringBuilder sb = new StringBuilder();
    boolean fFirst = false;

    for ( String term : terms )
    {
      if ( !fFirst )
        fFirst = true;
      else
        sb.append( "," );

      sb.append( "\"" + shortUrl(term) + "\"" );
    }

    return sb.toString();
  }

  String jsObjsToCSV( List<String> jsObjs )
  {
    StringBuilder sb = new StringBuilder();
    boolean fFirst = false;

    for ( String jsObj : jsObjs )
    {
      if ( !fFirst )
        fFirst = true;
      else
        sb.append(",");

      sb.append(jsObj);
    }

    return sb.toString();
  }

  static String URLDecode(String x)
  {
    try
    {
      return java.net.URLDecoder.decode(x, "UTF-8");
    }
    catch( Exception e )
    {
      return null;
    }
  }

  static String URLEncode(String x)
  {
    try
    {
      return java.net.URLEncoder.encode(x,"UTF-8");
    }
    catch( Exception e )
    {
      return null;
    }
  }

  static String readFile(String filename)
  {
    try
    {
      return new java.util.Scanner(new File(filename)).useDelimiter("\\A").next();
    }
    catch(Exception e)
    {
      return null;
    }
  }

  static OWLOntology loadOwlkbOntology( File kbFile, String kbFilename, OWLOntologyManager manager, OWLOntologyLoaderConfiguration config )
  {
    try
    {
      return manager.loadOntologyFromOntologyDocument(new FileDocumentSource(kbFile),config);
    }
    catch ( UnparsableOntologyException e )
    {
      System.out.println( "The ontology could not be parsed: " + kbFilename );
      e.printStackTrace();
    }
    catch( UnloadableImportException e )
    {
      System.out.println( "The ontology could not be loaded because of an 'UnloadableImportException'" );
      System.out.println( "This means the ontology tried to import one or more ontologies, one of which failed to load." );

      try
      {
        System.out.println( "The offending import was:" );
        System.out.println( e.getImportsDeclaration().getIRI().toString() );
      }
      catch( Exception e2 )
      {
        System.out.println( "Embarrassingly, an error occurred while trying to get details about the offending import." );
        System.out.println( "------" );
        System.out.println( "Stacktrace of import error:" );
        e.printStackTrace();
        System.out.println( "------" );
        System.out.println( "Stacktrace of error trying to get info about offending import:" );
        e2.printStackTrace();
      }
    }
    catch ( OWLOntologyCreationIOException e )
    {
      System.out.println( "Could not open " + kbFilename );
      System.out.println( "Details:" );
      System.out.println( e.getMessage() );
      System.out.println( "--------" );
      System.out.println( "To specify a different filename, run with -file <filename>" );
      System.out.println( "Also, make sure java has permission to access the file." );
    }
    catch ( OWLOntologyDocumentAlreadyExistsException e )
    {
      System.out.println( "Could not open " + kbFilename + ": document already exists?" );
      System.out.println( "This might occur, for example, if the ontology attempts to import the same subontology multiple times, or if some imported ontology itself imports some other imported ontology" );
    }
    catch ( OWLOntologyAlreadyExistsException e )
    {
      System.out.println( "Could not open " + kbFilename + ": document already exists?" );
      System.out.println( "This might occur, for example, if the ontology attempts to import the same subontology multiple times, or if some imported ontology itself imports some other imported ontology" );
    }
    catch ( OWLOntologyCreationException e )
    {
      System.out.println( "Could not load file: "+kbFilename );
      System.out.println( "An unknown OWL Ontology Creation Exception prevented the load." );
      System.out.println( "------" );
      System.out.println( "Details:" );
      System.out.println( e.getMessage() );
      System.out.println( "------" );
      System.out.println( "Stack trace:" );
      e.printStackTrace();
    }
    catch ( Exception e )
    {
      System.out.println( "Could not load file: "+kbFilename );
      System.out.println( "An unknown exception prevented the load." );
      System.out.println( "Stack trace:" );
      e.printStackTrace();
    }
    finally
    {
      return null;
    }
  }
}

