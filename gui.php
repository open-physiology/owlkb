<html>
<head>
<title>Owlkb Demonstration GUI</title>


<link rel="stylesheet" href="//www.semitrivial.com/base.css">
<link rel="stylesheet" href="//www.semitrivial.com/skeleton.css">
<link rel="stylesheet" href="owlkb.css">

<script type="text/javascript" src="owlkb.js"></script>

<style media='screen' type='text/css'>
#leftdiv {
        background-image: url(http://www.semitrivial.com/swirl/swirly.png);
        background-position: center;
        background-size: contain;
        background-repeat: no-repeat;
        }
</style>

@JAVASCRIPT

</head>
<body>

<div class='container' id='maindiv'>
  <div class='eight columns'>
    <h1>Owlkb Demo GUI</h1>
  </div>
  <div class='eight columns'>
    <span>&raquo; <a href='http://open-physiology.net/owlkb/ricordo.owl'>View ricordo.owl</a></span>
    <br>
    <span>&raquo; <a href='http://open-physiology.net/owlkb/doc.html'>Documentation</a></span>
  </div>
  <div class='sixteen columns'>
    <hr>
  </div>

  <div class='seven columns' id='leftdiv'>
    <h2>Instructions</h2>
    <div>
      Enter a query using Manchester query syntax.
      If the class does not exist, it will be created.
      <ul>
      <h3>Example queries:</h3>
      <li>CHEBI_37527</li>
      <li>CHEBI_37527 and participates-in some HP_0000118</li>
      <li>SINGLE-DOSE-PK-MODEL</li>
      <li>CHEBI_37527 and participates-in some SINGLE-DOSE-PK-MODEL</li>
      <li>PK-MODEL and MODEL-RELATED-TO-DRUG some CHEBI_27732</li>
      <li>part-of some SINGLE-DOSE-PK-MODEL</li>
      <li>part-of some SINGLE-DOSE-PK-MODEL and part-of some STEADY-STATE-PK-MODEL</li>
      <li>inheres-in some (part-of some INFUSION-2CPT-PK-MODEL)</li>
      <li>CHEBI_37527 and part-of some (PD-MODEL and 2CPT-PK-MODEL)</li>
      <li>Etc...  "The limit is your imagination"</li>
      </ul>
    </div>
  </div>

  <div class='nine columns' id='rightdiv' style='background-color:#F1F1F0'>
    <div id='rightpadding'>
      <input id="inputbox" onKeyUp="inputkey(event);" style="width:100%">
      <div>
        <button onclick="manchester_run(g('inputbox').value);" style="width:100%">Enter</button>
      </div>
      <div id="manchester_results">
      </div>
    </div>
  </div>

</div>

</body>
