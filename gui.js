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
  {
    manchester_run(g("inputbox").value);
    //g("inputbox").value="";
  }
}

function manchester_run(query)
{
  var url = "/test/" + encodeURIComponent(query);
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
      g("manchester_results").innerHTML = xmlhttp.responseText;
    else if ( xmlhttp.readyState==4 && xmlhttp.status != 200 )
      g("manchester_results").innerHTML = "There was an error ("+xmlhttp.status+") getting the results.  Some possible reasons (not exhaustive): 1. The Owlkb server is down.  2. Your internet is disconnected.";
  }

  xmlhttp.open("GET", url, true);

  xmlhttp.send();

  g("manchester_results").innerHTML = "...Loading...";
}
