import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import com.sun.net.httpserver.Headers;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.Scanner;
import java.util.ArrayList;

public class KBCaller
{
  private String url;

  public KBCaller( String url )
  {
    this.url = url;
  }

  /*
   * Minimalist self-contained partial JSON parser, for if you
   * want to get results as ArrayList<String> instead of as String
   */
  public ArrayList<String> parse_json( String json ) throws IOException
  {
    return parse_json_dont_clutter_top_of_file_with_code( json );
  }

  /*
   * Get all subterms of given term
   */
  public String subterms( String exp ) throws IOException
  {
    return launch_http( "/subterms/" + encode( exp ) );
  }

  /*
   * Get all sublings of given term.
   * Term X is a subling of term Y if there is an immediate
   * superterm Z of X such that Y is an immediate subterm
   * of Z.
   */
  public String siblings( String exp ) throws IOException
  {
    return launch_http( "/siblings/" + encode( exp ) );
  }

  /*
   * Get subterms of a given term, in a hierarchical JSON format
   */
  public String subhierarchy( String exp ) throws IOException
  {
    return launch_http( "/subhierarchy/" + encode( exp ) );
  }

  /*
   * Get all terms equivalent to given term
   */
  public String eqterms( String exp ) throws IOException
  {
    return launch_http( "/eqterms/" + encode( exp ) );
  }

  /*
   * Get all terms equivalent to, or subclass of, given term
   */
  public String terms( String exp ) throws IOException
  {
    return launch_http( "/terms/" + encode( exp ) );
  }

  /*
   * Get all individuals that are instances of given term
   */
  public String instances( String exp ) throws IOException
  {
    return launch_http( "/instances/" + encode( exp ) );
  }

  /*
   * Get all rdfs:labels of given term
   */
  public String labels( String exp ) throws IOException
  {
    return launch_http( "/labels/" + encode( exp ) );
  }

  /*
   * Get all terms with given rdfs:label
   */
  public String search( String label ) throws IOException
  {
    return launch_http( "/search/" + encode( label ) );
  }

  /*
   * Attempt to add label to given term
   */
  public void addlabel( String exp ) throws IOException
  {
    launch_http( "/addlabel/" + encode( exp ) );
  }

  private String encode( String exp )
  {
    try
    {
      return URLEncoder.encode( exp, "UTF-8" );
    }
    catch( Exception e )
    {
      return exp;
    }
  }

  private String launch_http( String cmd ) throws IOException
  {
    URL u;
    HttpURLConnection c;

    try
    {
      u = new URL(url + cmd);
      c = (HttpURLConnection) u.openConnection();

      c.setConnectTimeout(2000);
      c.setReadTimeout(5000);
    }
    catch ( Exception e )
    {
      throw new IOException("Could not connect to OWLKB");
    }

    c.setRequestProperty( "Accept", "application/json" );

    Scanner sc = null;
    try
    {
      sc = new Scanner(c.getInputStream( ) );
    }
    catch( Exception e )
    {
      throw new IOException("Could not read from OWLKB");
    }

    return sc.useDelimiter("\\A").next();
  }

  private ArrayList<String> parse_json_dont_clutter_top_of_file_with_code( String j ) throws IOException
  {
    ArrayList<String> L = new ArrayList<String>();

    if ( j.charAt(0) != '[' || j.charAt(j.length()-1) != ']' )
      throw new IOException( "String is not a JSON list" );

    String inside = j.substring(1, j.length()-1).trim();
    String[] split = inside.split(",");

    for ( int i = 0; i < split.length; i++ )
    {
      String entry = split[i].trim();

      if ( entry.charAt(0) != '\'' || entry.charAt(entry.length()-1) != '\'' )
        throw new IOException( "String is not a properly enquoted JSON list ("+entry+")" );

      L.add( entry.substring(1,entry.length()-1) );
    }

    return L;
  }
}
