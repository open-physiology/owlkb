/*
 * Owlkb, part of RICORDO.
 * Programmed by Sarala Wimaralatne, reprogrammed by Sam Alexander.
 * This is a private release, please wait for the public release for official license details.
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;
import java.net.InetSocketAddress;
import java.io.*;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;

import org.semanticweb.HermiT.Reasoner;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.coode.owlapi.manchesterowlsyntax.ManchesterOWLSyntaxEditorParser;
import org.semanticweb.owlapi.util.BidirectionalShortFormProvider;
import org.semanticweb.owlapi.util.BidirectionalShortFormProviderAdapter;
import org.semanticweb.owlapi.util.SimpleShortFormProvider;
import org.semanticweb.owlapi.expression.OWLEntityChecker;
import org.semanticweb.owlapi.expression.ShortFormEntityChecker;

public class Owlkb
{
  /*
   * Load the ontology and launch a server on port 20080 to serve
   * the owlkb API, as described at http://www.semitrivial.com/owlkb/api.php
   */
  public static void main(String [] args) throws Exception
  {
    /*
     * Load the main ontology
     */
    String kbNs = "http://www.ricordo.eu/ricordo.owl";
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

    logstring( "Loading ontology...");

    OWLOntology ont = manager.loadOntologyFromOntologyDocument(new File("/home/sarala/testkb/ricordo.owl"));  //Location of OWL file

    logstring( "Ontology is loaded.");

    IRI iri = manager.getOntologyDocumentIRI(ont);

    /*
     * Load the ontologies imported by the main ontology (e.g., the reference ontologies)
     */
    Set<OWLOntology> imp_closure = ont.getImportsClosure();

    /*
     * Establish infrastructure for converting long URLs to short IRIs and vice versa
     * (e.g., converting between "http://purl.org/obo/owlapi/quality#PATO_0000014" and "PATO_0000014")
     */
    BidirectionalShortFormProvider shorts = new BidirectionalShortFormProviderAdapter(manager, imp_closure, new SimpleShortFormProvider());
    OWLEntityChecker entityChecker = new ShortFormEntityChecker(shorts);

    /*
     * Initiate the reasoner
     */
    logstring( "Establishing reasoner...");

    OWLReasoner r;

    if ( args.length==0 || args[0].toLowerCase().equals("elk") )
    {
      OWLReasonerFactory rf = new ElkReasonerFactory();
      r = rf.createReasoner(ont);
    }
    else if ( args[0].toLowerCase().equals("hermit") )
      r = new Reasoner(ont);
    else
    {
      System.out.println("Unrecognized reasoner specified.  Valid reasoners are Elk and HermiT.");
      System.out.println("");
      return;
    }

    logstring( "Reasoner established.");

    /*
     * Precompute inferences.
     */
    logstring( "Precomputing inferences...");

    long start_time = System.nanoTime();
    r.precomputeInferences(InferenceType.CLASS_HIERARCHY);

    logstring( "Finished precomputing inferences (took "+(System.nanoTime()-start_time)/1000000+"ms)" );

    /*
     * Launch HTTP server
     */
    logstring( "Initiating server...");

    HttpServer server = HttpServer.create(new InetSocketAddress(20080), 0 );
    server.createContext("/subterms", new NetHandler("subterms", r, manager, ont, entityChecker, kbNs, iri));
    server.createContext("/eqterms", new NetHandler("eqterms", r, manager, ont, entityChecker, kbNs, iri));
    server.createContext("/terms", new NetHandler("terms", r, manager, ont, entityChecker, kbNs, iri));
    server.createContext("/test", new NetHandler("test", r, manager, ont, entityChecker, kbNs, iri));

    server.setExecutor(null);
    server.start();

    logstring( "Server initiated.");

    /*
     * The program will now go dormant, waking up when it hears API requests
     */
  }

  static class NetHandler implements HttpHandler
  {
    String srvtype;
    OWLReasoner r;
    OWLOntologyManager m;
    OWLOntology o;
    OWLEntityChecker ec;
    String kbNs;
    IRI iri;

    public NetHandler(String srvtype, OWLReasoner r, OWLOntologyManager m, OWLOntology o, OWLEntityChecker ec, String kbNs, IRI iri)
    {
      this.srvtype = srvtype;
      this.r = r;
      this.m = m;
      this.o = o;
      this.ec = ec;
      this.kbNs = kbNs;
      this.iri = iri;
    }

    public void handle(HttpExchange t) throws IOException
    {
      String response = "<table><tr><th>ID</th></tr>";
      String req = t.getRequestURI().toString().substring(2+srvtype.length());
      req = java.net.URLDecoder.decode(req, "UTF-8");

      ManchesterOWLSyntaxEditorParser parser = new ManchesterOWLSyntaxEditorParser(m.getOWLDataFactory(), req);
      parser.setDefaultOntology(o);
      parser.setOWLEntityChecker(ec);

      logstring( "Got request: ["+req+"]" );
      long start_time = System.nanoTime();

      try
      {
        OWLClassExpression exp;

        try
        {
          exp = parser.parseClassExpression();
        }
        catch (Exception e)
        {
          exp = null;
        }

        if ( exp == null )
        {
          response = "Malformed Manchester query";
        }
        else
        {
          if ( srvtype.equals("subterms")
          ||   srvtype.equals("eqterms")
          ||   srvtype.equals("terms") )
          {
            ArrayList<Term> terms = null;

            if ( srvtype.equals("subterms") )
              terms = getSubTerms(exp,r);
            else if ( srvtype.equals("eqterms") )
              terms = addTerm(exp, r, m, kbNs, o, iri);
            else if ( srvtype.equals("terms") )
              terms = getTerms(exp,r);

            for ( Term termp : terms )
            {
              response += "<tr><td>"+shorturl(termp.getId())+"</td></tr>";
            }
            response += "</table>";
          }
          else if ( srvtype.equals("test") )
          {
            ArrayList<Term> terms;

            try
            {
              terms = addTerm(exp, r, m, kbNs, o, iri);
            }
            catch(Exception e)
            {
              terms = null;
            }

            if ( terms == null )
            {
              response = "Malformed Manchester query";
            }
            else
            {
              response = "<h3>Term</h3><ul>";

              for ( Term termp : terms )
              {
                response += "<li>" + shorturl(termp.getId()) + "</li>";
              }
              response += "</ul>";

              try
              {
                ArrayList<Term> subs = getSubTerms(exp,r);

                if ( subs.size() != 0 )
                {
                  response += "<h3>Subterms</h3><ul>";

                  for ( Term termp : subs )
                  {
                    response += "<li>"+shorturl(termp.getId())+"</li>";
                  }
                  response += "</ul>";
                }
              }
              catch(Exception e)
              {
                response = "There was an error getting the results";
              }

              response += "<h5>Runtime</h5>This computation took "+(System.nanoTime() - start_time) / 1000000+"ms to complete";
            }
          }
          else
            response = "Unrecognized request";
        }
      }
      catch(Exception e)
      {
        response = "There was an error getting the results.";
      }

      logstring( "Transmitting response..." );

      Headers h = t.getResponseHeaders();
      h.add("Cache-Control", "no-cache, no-store, must-revalidate");
      h.add("Pragma", "no-cache");
      h.add("Expires", "0");

      t.sendResponseHeaders(200,response.getBytes().length);
      OutputStream os = t.getResponseBody();
      os.write(response.getBytes());
      os.close();

      logstring( "Response transmitted.");

      /*
       * Measure computation time in ms.
       */
      long runtime = (System.nanoTime() - start_time) / 1000000;
      logstring( "It took "+runtime+"ms to handle the request." );
    }
  }

  /*
   * The following methods (getSubTerms, getEquivalentTerms, getTerms, addTerm)
   * are adapted from methods of the same names written by Sarala W.
   */
  private static ArrayList<Term> getSubTerms(OWLClassExpression exp, OWLReasoner r)
  {
    ArrayList<Term> idList = new ArrayList<Term>();
    NodeSet<OWLClass> subClasses = r.getSubClasses(exp, false);

    if (subClasses!=null)
    {
      for (Node<OWLClass> owlClassNode : subClasses)
      {
        idList.add(new Term(owlClassNode.getEntities().iterator().next().toStringID()));
      }
    }

    return idList;
  }

  private static ArrayList<Term> getEquivalentTerms(OWLClassExpression exp, OWLReasoner r)
  {
    ArrayList<Term> idList = new ArrayList<Term>();
    Node<OWLClass> equivalentClasses = r.getEquivalentClasses(exp);

    if(equivalentClasses != null)
    {
      try
      {
        idList.add(new Term(equivalentClasses.getEntities().iterator().next().toStringID()));
      }
      catch (java.util.NoSuchElementException e)
      {
        ;
      }
    }

    return idList;
  }

  public static ArrayList<Term> getTerms(OWLClassExpression exp, OWLReasoner r)
  {
    ArrayList<Term> idList = new ArrayList<Term>();

    idList.addAll(getEquivalentTerms(exp,r));
    idList.addAll(getSubTerms(exp,r));

    return idList;
  }

  public static ArrayList<Term> addTerm(OWLClassExpression exp, OWLReasoner r, OWLOntologyManager mgr, String kbNs, OWLOntology ont, IRI iri)
  {
    logstring( "addTerm called..." );

    ArrayList<Term> idList = getEquivalentTerms(exp,r);
    if(idList.isEmpty())
    {
      logstring( "Term is new, adding it..." );

      String ricordoid = String.valueOf(System.currentTimeMillis());
      OWLClass newowlclass = mgr.getOWLDataFactory().getOWLClass(IRI.create(kbNs + "#RICORDO_" + ricordoid));

      OWLAxiom axiom = mgr.getOWLDataFactory().getOWLEquivalentClassesAxiom(newowlclass, exp);
      Set<OWLAxiom> axiomSet = new HashSet<OWLAxiom>();
      axiomSet.add(axiom);

      mgr.addAxioms(ont,axiomSet);
      r.flush();

      logstring( "New term added to ontology in RAM." );

      /*
       * Temporarily disable saving ontology to hard drive
       *
      logstring( "Saving ontology to hard drive..." );

      try
      {
        mgr.saveOntology(ont,iri);
      }
      catch ( OWLOntologyStorageException e )
      {
        e.printStackTrace(); //To do: proper error handling here
      }

      logstring( "Finished saving ontology to hard drive." );
       *
       */

      logstring( "Precomputing inferences..." );

      idList.add(new Term(newowlclass.toStringID()));
      r.precomputeInferences(InferenceType.CLASS_HIERARCHY);

      logstring( "Finished precomputing inferences." );
    }
    else
      logstring( "Term already exists, no need to add." );

    return idList;
  }

  /*
   * Convert long URL to short
   * e.g. convert "http://purl.org/obo/owlapi/quality#PATO_0000014" to "PATO_0000014"
   */
  public static String shorturl(String url)
  {
    return url.substring(url.indexOf("#")+1);
  }

  /*
   * Basic logging to stdout
   */
  public static void logstring( String x )
  {
    System.out.println( x );
  }

  /*
   * The following Term class was written by Sarala W.
   * (To do: root it out and replace it by String)
   */
  static class Term
  {
    private String id;

    public Term() {}

    public Term(String id)
    {
        this.id = id;
    }

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }
  }
}
