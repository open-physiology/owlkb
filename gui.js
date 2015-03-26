if(typeof String.prototype.trim !== 'function') {
  String.prototype.trim = function() {
    return this.replace(/^\s+|\s+$/g, '');
  }
}

function g(x)
{
 return document.getElementById(x);
}

function inputkey(e)
{
  if (e.keyCode=='13' && g("inputbox").value != "")
    manchester_run(g("inputbox").value);
}

function manchester_run(query)
{
  var url = "/test/" + encodeURIComponent(query) + "?verbose";
  var xmlhttp;

  if ( query == "" )
    return;

  if ( window.XMLHttpRequest )
    xmlhttp = new XMLHttpRequest();
  else
    xmlhttp = new ActiveXObject("Microsoft.XMLHTTP");

  xmlhttp.onreadystatechange=function()
  {
    if ( xmlhttp.readyState==4 && xmlhttp.status==200 )
      g("manchester_results").innerHTML = parse_demo_json(xmlhttp.responseText);
    else if ( xmlhttp.readyState==4 && xmlhttp.status != 200 )
      g("manchester_results").innerHTML = "There was an error ("+xmlhttp.status+") getting the results.  Some possible reasons (not exhaustive): 1. The Owlkb server is down.  2. Your internet is disconnected.";
  }

  xmlhttp.open("GET", url, true);

  xmlhttp.send();

  g("manchester_results").innerHTML = "...Loading...";
}

function describe_unlabeled_class( term )
{
  if ( term == "http://www.w3.org/2002/07/owl#Nothing" )
    return "(Nothing -- the empty class)";
  else
    return "(Unlabeled class)";
}

function parse_demo_json( x )
{
  try
  {
    x = JSON.parse(x);
  }
  catch( err )
  {
    return x;
  }
  var retval;

  if ( x.terms.length == 1 )
    retval = "<h3>Term</h3><ul>";
  else
    retval = "<h3>Terms</h3><ul>";

  if ( x.terms.length == 0 )
    retval += "<li>None</li>";
  else
  for ( var i = 0; i < x.terms.length; i++ )
  {
    if ( x.terms[i].label == null )
      retval += "<li>&raquo; " + describe_unlabeled_class( x.terms[i].term );
    else
      retval += "<li>&raquo; "+x.terms[i].label;

    retval += "<ul><li style='font-size:x-small'>" + x.terms[i].term + "</li></ul></li>";
  }

  retval += "</ul>";

  if ( x.subterms.length == 1 )
    retval += "<h3>Subterm</h3><ul>";
  else
    retval += "<h3>Subterms</h3><ul>";

  if ( x.subterms.length == 0 )
    retval += "<li>None</li>";
  else
  {
    for ( var i = 0; i < x.subterms.length; i++ )
    {
      if ( x.subterms[i].label == null )
        retval += "<li>&raquo; " + describe_unlabeled_class( x.subterms[i].term );
      else
        retval += "<li>&raquo; " + x.subterms[i].label;

      retval += "<ul><li style='font-size:x-small'>"+x.subterms[i].term+"</li></ul></li>";
    }
  }

  retval += "</ul>";

  retval += "<h5>Runtime</h5>";

  retval += "This computation took " + x.runtime + " to complete"

  return retval;
}
