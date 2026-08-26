<html>  
<body>  
<script type="text/javascript">  
var UserAgent = navigator.userAgent.toLowerCase();
var browser=navigator.appName;  
var b_version=navigator.appVersion;  
var version=parseFloat(b_version);  
document.write("Browser name: "+ browser);  
document.write("<br />");  
document.write("Browser version: "+ version);  

if(UserAgent.indexOf("mobile") == -1){
   document.write("desktop");  
}
else{
   document.write("mobile");  
}

</script>  
</body>  
</html>