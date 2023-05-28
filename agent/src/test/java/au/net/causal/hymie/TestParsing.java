package au.net.causal.hymie;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.impl.io.ChunkedInputStream;
import org.apache.hc.core5.http.impl.io.ContentLengthInputStream;
import org.apache.hc.core5.http.impl.io.DefaultHttpRequestParser;
import org.apache.hc.core5.http.impl.io.DefaultHttpResponseParser;
import org.apache.hc.core5.http.impl.io.IdentityInputStream;
import org.apache.hc.core5.http.impl.io.SessionInputBufferImpl;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.apache.hc.core5.http.io.entity.EmptyInputStream;
import org.apache.hc.core5.http.message.BasicLineParser;
import org.apache.hc.core5.io.Closer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class TestParsing
{
    private final ContentLengthStrategy contentLengthStrategy = new DefaultContentLengthStrategy();

    @Test
    void testRequest()
    throws Exception
    {
        String s = """
GET / HTTP/1.1
user-agent: ReactorNetty/1.1.7
host: www.google.com
accept: */*

""";

        DefaultHttpRequestParser parser = new DefaultHttpRequestParser();

        SessionInputBufferImpl buf = new SessionInputBufferImpl(65536);

        ClassicHttpRequest result = parser.parse(buf, new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
        System.out.println(result);
    }

    @Test
    void testResponse()
    throws Exception
    {
        Http1Config http1Config = Http1Config.DEFAULT;
        DefaultHttpResponseParser parser = new DefaultHttpResponseParser(new BasicLineParser(), null, http1Config);

        String s = """
HTTP/1.1 200 OK
Date: Thu, 25 May 2023 00:08:10 GMT
Expires: -1
Cache-Control: private, max-age=0
Content-Type: text/html; charset=ISO-8859-1
Content-Security-Policy-Report-Only: object-src 'none';base-uri 'self';script-src 'nonce-CdpXddpU6nOBXFMlL6vZUQ' 'strict-dynamic' 'report-sample' 'unsafe-eval' 'unsafe-inline' https: http:;report-uri https://csp.withgoogle.com/csp/gws/other-hp
P3P: CP="This is not a P3P policy! See g.co/p3phelp for more info."
Server: gws
X-XSS-Protection: 0
X-Frame-Options: SAMEORIGIN
Set-Cookie: 1P_JAR=2023-05-25-00; expires=Sat, 24-Jun-2023 00:08:10 GMT; path=/; domain=.google.com; Secure
Set-Cookie: AEC=AUEFqZfwwPIYaopywRNhAZoNyczO64ch9kKjNcoZmw1M0BZkeTnh04rM-w; expires=Tue, 21-Nov-2023 00:08:10 GMT; path=/; domain=.google.com; Secure; HttpOnly; SameSite=lax
Set-Cookie: NID=511=JBuptjBdVx6ORZ9iHfjXFPVwuRZn27H55EeGS0mqUlELu-IVa6foYc-DthQLRyWiuL4hEvYp8aN-3JUIBNJ6Ba3FxrtDALBc1xXvOUOqqns98g9wKBW094K0DMN_kEPbxThh4_CftKakXoArMwCduzSvFuUlK6QwQVMQIzIscPg; expires=Fri, 24-Nov-2023 00:08:10 GMT; path=/; domain=.google.com; HttpOnly
Alt-Svc: h3=":443"; ma=2592000,h3-29=":443"; ma=2592000
Accept-Ranges: none
Vary: Accept-Encoding
Transfer-Encoding: chunked

33ed
<!doctype html><html itemscope="" itemtype="http://schema.org/WebPage" lang="en-AU"><head><meta content="text/html; charset=UTF-8" http-equiv="Content-Type"><meta content="/logos/doodles/2023/celebrating-the-big-mango-6753651837110047-l.png" itemprop="image"><meta content="Celebrating the Big Mango" property="twitter:title"><meta content="Celebrating the Big Mango! #GoogleDoodle" property="twitter:description"><meta content="Celebrating the Big Mango! #GoogleDoodle" property="og:description"><meta content="summary_large_image" property="twitter:card"><meta content="@GoogleDoodles" property="twitter:site"><meta content="https://www.google.com/logos/doodles/2023/celebrating-the-big-mango-6753651837110047-2x.png" property="twitter:image"><meta content="https://www.google.com/logos/doodles/2023/celebrating-the-big-mango-6753651837110047-2x.png" property="og:image"><meta content="1000" property="og:image:width"><meta content="400" property="og:image:height"><title>Google</title><script nonce="CdpXddpU6nOBXFMlL6vZUQ">(function(){var _g={kEI:'6qZuZOSfEY2KseMPvOiR2Ak',kEXPI:'0,1359409,6058,207,4804,2316,383,246,5,1129120,1704,1196083,104,380599,16115,28684,22431,1361,12319,17580,4998,13123,105,3847,38444,2872,2891,4140,7614,606,29842,826,30022,16105,230,1014,1,16916,2652,4,1528,2304,42126,13659,4437,14995,7618,2530,4094,7596,1,42154,2,14022,2715,23024,5679,1020,25048,6075,4568,6255,23419,1254,5835,14968,4332,7484,445,2,2,1,6959,3997,13670,2006,8155,7381,2,1477,14491,872,19634,6,1923,9779,12414,30045,2007,18191,17624,2513,14,82,20206,8377,8238,10750,5375,3030,6111,9705,1804,7734,2738,490,2395,9480,10260,4585,2222,3609,542,1330,1697,342,908,382,2483,3318,2,2148,3415,1052,1442,1129,7343,1287,656,4,449,1974,1991,1366,2802,411,552,129,1711,279,2545,3,1076,9,2889,1780,654,289,601,1078,8,3,7,284,1092,3238,643,319,44,214,51,125,9,283,2,165,1457,372,1023,79,2,117,534,21,4,1975,5206901,556,8,37,35,51,2,5993730,532,2803892,3311,141,795,19735,1,1,348,5995,100,95,6,8,3,9,6,4,7,11,21,23944218,578,2737886,1303678,1964,1008,15664,2894,6250,14712,5176,49,1675,744,1408440,260489,23499463,84,94,134,1568,2,473,4,558,2,255,292,767,248,2,350,972,334,5,43,163,451,333,138,1,208,2302,727,170,431,1006,577,1804,327,8,3,137,807,53,42,764,134,349,269,224,254,939,9,113,145,470,99,72,65,123,301,5,333,657,1341,143,108,718,684,490,315,150,163,193,1,6,1018,11,34,200,188,64,2,297,16,91,134,3,661,261,54,4,170,615,922,38,617,194,105,73,163,2,415,600,2,2,78,16,335,181',kBL:'Pz-6',kOPI:89978449};if (!window.google || !window.google.stvsc){window.google = _g;}
})();(function(){google.sn='webhp';google.kHL='en-AU';})();(function(){
var h=this||self;function l(){return void 0!==window.google&&void 0!==window.google.kOPI&&0!==window.google.kOPI?window.google.kOPI:null};var m,n=[];function p(a){for(var b;a&&(!a.getAttribute||!(b=a.getAttribute("eid")));)a=a.parentNode;return b||m}function q(a){for(var b=null;a&&(!a.getAttribute||!(b=a.getAttribute("leid")));)a=a.parentNode;return b}function r(a){/^http:/i.test(a)&&"https:"===window.location.protocol&&(google.ml&&google.ml(Error("a"),!1,{src:a,glmm:1}),a="");return a}
function t(a,b,c,d,k){var e="";-1===b.search("&ei=")&&(e="&ei="+p(d),-1===b.search("&lei=")&&(d=q(d))&&(e+="&lei="+d));d="";var g=-1===b.search("&cshid=")&&"slh"!==a,f=[];f.push(["zx",Date.now().toString()]);h._cshid&&g&&f.push(["cshid",h._cshid]);c=c();null!=c&&f.push(["opi",c.toString()]);for(c=0;c<f.length;c++){if(0===c||0<c)d+="&";d+=f[c][0]+"="+f[c][1]}return"/"+(k||"gen_204")+"?atyp=i&ct="+String(a)+"&cad="+(b+e+d)};m=google.kEI;google.getEI=p;google.getLEI=q;google.ml=function(){return null};google.log=function(a,b,c,d,k,e){e=void 0===e?l:e;c||(c=t(a,b,e,d,k));if(c=r(c)){a=new Image;var g=n.length;n[g]=a;a.onerror=a.onload=a.onabort=function(){delete n[g]};a.src=c}};google.logUrl=function(a,b){b=void 0===b?l:b;return t("",a,b)};}).call(this);(function(){google.y={};google.sy=[];google.x=function(a,b){if(a)var c=a.id;else{do c=Math.random();while(google.y[c])}google.y[c]=[a,b];return!1};google.sx=function(a){google.sy.push(a)};google.lm=[];google.plm=function(a){google.lm.push.apply(google.lm,a)};google.lq=[];google.load=function(a,b,c){google.lq.push([[a],b,c])};google.loadAll=function(a,b){google.lq.push([a,b])};google.bx=!1;google.lx=function(){};}).call(this);google.f={};(function(){
document.documentElement.addEventListener("submit",function(b){var a;if(a=b.target){var c=a.getAttribute("data-submitfalse");a="1"===c||"q"===c&&!a.elements.q.value?!0:!1}else a=!1;a&&(b.preventDefault(),b.stopPropagation())},!0);document.documentElement.addEventListener("click",function(b){var a;a:{for(a=b.target;a&&a!==document.documentElement;a=a.parentElement)if("A"===a.tagName){a="1"===a.getAttribute("data-nohref");break a}a=!1}a&&b.preventDefault()},!0);}).call(this);</script><style>#gbar,#guser{font-size:13px;padding-top:1px !important;}#gbar{height:22px}#guser{padding-bottom:7px !important;text-align:right}.gbh,.gbd{border-top:1px solid #c9d7f1;font-size:1px}.gbh{height:0;position:absolute;top:24px;width:100%}@media all{.gb1{height:22px;margin-right:.5em;vertical-align:top}#gbar{float:left}}a.gb1,a.gb4{text-decoration:underline !important}a.gb1,a.gb4{color:#00c !important}.gbi .gb4{color:#dd8e27 !important}.gbf .gb4{color:#900 !important}
</style><style>body,td,a,p,.h{font-family:arial,sans-serif}body{margin:0;overflow-y:scroll}#gog{padding:3px 8px 0}td{line-height:.8em}.gac_m td{line-height:17px}form{margin-bottom:20px}.h{color:#1558d6}em{font-weight:bold;font-style:normal}.lst{height:25px;width:496px}.gsfi,.lst{font:18px arial,sans-serif}.gsfs{font:17px arial,sans-serif}.ds{display:inline-box;display:inline-block;margin:3px 0 4px;margin-left:4px}input{font-family:inherit}body{background:#fff;color:#000}a{color:#4b11a8;text-decoration:none}a:hover,a:active{text-decoration:underline}.fl a{color:#1558d6}a:visited{color:#4b11a8}.sblc{padding-top:5px}.sblc a{display:block;margin:2px 0;margin-left:13px;font-size:11px}.lsbb{background:#f8f9fa;border:solid 1px;border-color:#dadce0 #70757a #70757a #dadce0;height:30px}.lsbb{display:block}#WqQANb a{display:inline-block;margin:0 12px}.lsb{background:url(/images/nav_logo229.png) 0 -261px repeat-x;color:#000;border:none;cursor:pointer;height:30px;margin:0;outline:0;font:15px arial,sans-serif;vertical-align:top}.lsb:active{background:#dadce0}.lst:focus{outline:none}</style><script nonce="CdpXddpU6nOBXFMlL6vZUQ">(function(){window.google.erd={jsr:1,bv:1800,de:true};
var h=this||self;var k,l=null!=(k=h.mei)?k:1,n,p=null!=(n=h.sdo)?n:!0,q=0,r,t=google.erd,v=t.jsr;google.ml=function(a,b,d,m,e){e=void 0===e?2:e;b&&(r=a&&a.message);if(google.dl)return google.dl(a,e,d),null;if(0>v){window.console&&console.error(a,d);if(-2===v)throw a;b=!1}else b=!a||!a.message||"Error loading script"===a.message||q>=l&&!m?!1:!0;if(!b)return null;q++;d=d||{};b=encodeURIComponent;var c="/gen_204?atyp=i&ei="+b(google.kEI);google.kEXPI&&(c+="&jexpid="+b(google.kEXPI));c+="&srcpg="+b(google.sn)+"&jsr="+b(t.jsr)+"&bver="+b(t.bv);var f=a.lineNumber;void 0!==f&&(c+="&line="+f);var g=
a.fileName;g&&(0<g.indexOf("-extension:/")&&(e=3),c+="&script="+b(g),f&&g===window.location.href&&(f=document.documentElement.outerHTML.split("\\n")[f],c+="&cad="+b(f?f.substring(0,300):"No script found.")));c+="&jsel="+e;for(var u in d)c+="&",c+=b(u),c+="=",c+=b(d[u]);c=c+"&emsg="+b(a.name+": "+a.message);c=c+"&jsst="+b(a.stack||"N/A");12288<=c.length&&(c=c.substr(0,12288));a=c;m||google.log(0,"",a);return a};window.onerror=function(a,b,d,m,e){r!==a&&(a=e instanceof Error?e:Error(a),void 0===d||"lineNumber"in a||(a.lineNumber=d),void 0===b||"fileName"in a||(a.fileName=b),google.ml(a,!1,void 0,!1,"SyntaxError"===a.name||"SyntaxError"===a.message.substring(0,11)||-1!==a.message.indexOf("Script error")?3:0));r=null;p&&q>=l&&(window.onerror=null)};})();</script></head><body bgcolor="#fff"><script nonce="CdpXddpU6nOBXFMlL6vZUQ">(function(){var src='/images/nav_logo229.png';var iesg=false;document.body.onload = function(){window.n && window.n();if (document.images){new Image().src=src;}
if (!iesg){document.f&&document.f.q.focus();document.gbqf&&document.gbqf.q.focus();}
}
})();</script><div id="mngb"><div id=gbar><nobr><b class=gb1>Search</b> <a class=gb1 href="https://www.google.com.au/imghp?hl=en&tab=wi">Images</a> <a class=gb1 href="https://maps.google.com.au/maps?hl=en&tab=wl">Maps</a> <a class=gb1 href="https://play.google.com/?hl=en&tab=w8">Play</a> <a class=gb1 href="https://www.youtube.com/?tab=w1">YouTube</a> <a class=gb1 href="https://news.google.com/?tab=wn">News</a> <a class=gb1 href="https://mail.google.com/mail/?tab=wm">Gmail</a> <a class=gb1 href="https://drive.google.com/?tab=wo">Drive</a> <a class=gb1 style="text-decoration:none" href="https://www.google.com.au/intl/en/about/products?tab=wh"><u>More</u> &raquo;</a></nobr></div><div id=guser width=100%><nobr><span id=gbn class=gbi></span><span id=gbf class=gbf></span><span id=gbe></span><a href="http://www.google.com.au/history/optout?hl=en" class=gb4>Web History</a> | <a  href="/preferences?hl=en" class=gb4>Settings</a> | <a target=_top id=gb_70 href="https://accounts.google.com/ServiceLogin?hl=en&passive=true&continue=https://www.google.com/&ec=GAZAAQ" class=gb4>Sign in</a></nobr></div><div class=gbh style=left:0></div><div class=gbh style=right:0></div></div><center><br clear="all" id="lgpd"><div id="lga"><a href="/search?ie=UTF-8&amp;q=The+Big+Mango&amp;oi=ddle&amp;ct=258247635&amp;hl=en-GB&amp;si=AMnBZoEZ8aFftZu792frFYrnK9KQYGXRL3UTeDeHB9-uc0sfFV573BQUfg4KXSThXJUVdMMo8d1CvkAJzEkaTKwjouS_98oKxef25GMSorlHHGvNIWYFLWJyF0r7rzJjX7Lz6bIDFTFGXf-AfIbOuI0Hvxq9gJw8APxTZ3lHDnFG0UlxodQ8r88%3D&amp;sa=X&amp;ved=0ahUKEwjkvLHTlo__AhUNRWwGHTx0BJsQPQgD"><img alt="Celebrating the Big Mango" border="0" height="200" src="/logos/doodles/2023/celebrating-the-big-mango-6753651837110047-l.png" title="Celebrating the Big Mango" width="500" id="hplogo"><br></a><br></div><form action="/search" name="f"><table cellpadding="0" cellspacing="0"><tr valign="top"><td width="25%">&nbsp;</td><td align="center" nowrap=""><input name="ie" value="ISO-8859-1" type="hidden"><input value="en-AU" name="hl" type="hidden"><input name="source" type="hidden" value="hp"><input name="biw" type="hidden"><input name="bih" type="hidden"><div class="ds" style="height:32px;margin:4px 0"><input class="lst" style="margin:0;padding:5px 8px 0 6px;vertical-align:top;color:#000" autocomplete="off" value="" title="Google Search" maxlength="2048" name="q" size="57"></div><br style="line-height:0"><span class="ds"><span class="lsbb"><input class="lsb" value="Google Search" name="btnG" type="submit"></span></span><span class="ds"><span class="lsbb"><input class="lsb" id="tsuid_1" value="I'm Feeling Lucky" name="btnI" type="submit"><script nonce="CdpXddpU6nOBXFMlL6vZUQ">(function(){var id='tsuid_1';document.getElementById(id).onclick = function(){if (this.form.q.value){this.checked = 1;if (this.form.iflsig)this.form.iflsig.disabled = false;}
else top.location='/doodles/';};})();</script><input value="AOEireoAAAAAZG60-oy_3zDc0SE2FQEVm0qFqqa0h-WN" name="iflsig" type="hidden"></span></span></td><td class="fl sblc" align="left" nowrap="" width="25%"><a href="/advanced_search?hl=en-AU&amp;authuser=0">Advanced search</a></td></tr></table><input id="gbv" name="gbv" type="hidden" value="1"><script nonce="CdpXddpU6nOBXFMlL6vZUQ">(function(){var a,b="1";if(document&&document.getElementById)if("undefined"!=typeof XMLHttpRequest)b="2";else if("undefined"!=typeof ActiveXObject){var c,d,e=["MSXML2.XMLHTTP.6.0","MSXML2.XMLHTTP.3.0","MSXML2.XMLHTTP","Microsoft.XMLHTTP"];for(c=0;d=e[c++];)try{new ActiveXObject(d),b="2"}catch(h){}}a=b;if("2"==a&&-1==location.search.indexOf("&gbv=2")){var f=google.gbvu,g=document.getElementById("gbv");g&&(g.value=a);f&&window.setTimeout(function(){location.href=f},0)};}).call(this);</script></form><div id="gac_scont"></div><div style="font-size:83%;min-height:3.5em"><br></div><span id="footer"><div style="font-size:10pt"><div style="margin:19px auto;text-align:center" id="WqQANb"><a href="/intl/en/ads/">Advertising</a><a href="/services/">Business Solutions</a><a href="/intl/en/about.html">About Google</a><a href="https://www.google.com/setprefdomain?prefdom=AU&amp;prev=https://www.google.com.au/&amp;sig=K_K6G1-tQqQvukkEIDi1m-1YZ4vN8%3D">Google.com.au</a></div></div><p style="font-size:8pt;color:#70757a">&copy; 2023 - <a href="/intl/en/policies/privacy/">Privacy</a> - <a href="/intl/en/policies/terms/">Terms</a></p></span></center><script nonce="CdpXddpU6nOBXFMlL6vZUQ">(function(){window.google.cdo={height:757,width:1440};(function(){var a=window.innerWidth,b=window.innerHeight;if(!a||!b){var c=window.document,d="CSS1Compat"==c.compatMode?c.documentElement:c.body;a=d.clientWidth;b=d.clientHeight}
if(a&&b&&(a!=google.cdo.width||b!=google.cdo.height)){var e=google,f=e.log,g="/client_204?&atyp=i&biw="+a+"&bih="+b+"&ei="+google.kEI,h="",k=[],l=void 0!==window.google&&void 0!==window.google.kOPI&&0!==window.google.kOPI?window.google.kOPI:null;null!=l&&k.push(["opi",l.toString()]);for(var m=0;m<k.length;m++){if(0===m||0<m)h+="&";h+=k[m][0]+"="+k[m][1]}f.call(e,"","",g+h)};}).call(this);})();</script> <script nonce="CdpXddpU6nOBXFMlL6vZUQ">(function(){google.xjs={ck:'xjs.hp.t7rKxWS_HtE
c9
.L.X.O',cs:'ACT90oGfCEiiReskj8FK3tPtqQ27okzpvA',csss:'ACT90oGLqVKo5CQUMWi7lf55wckZgg5pqw',excm:[],sepcss:false};})();</script>  <script nonce="CdpXddpU6nOBXFMlL6vZUQ">(function(){var u='/xjs/_/js/k\\x3d
127f
xjs.hp.en.AVR0hGrCVGs.O/am\\x3dAAAA6AQAUABgAQ/d\\x3d1/ed\\x3d1/rs\\x3dACT90oHCJdGcARzpNH4ZZe9SohhN4iV9sQ/m\\x3dsb_he,d';var amd=0;
var e=this||self,f=function(c){return c};var g;var k=function(c){this.g=c};k.prototype.toString=function(){return this.g+""};var m={};
function q(){var c=u,n=function(){};google.lx=google.stvsc?n:function(){google.timers&&google.timers.load&&google.tick&&google.tick("load","xjsls");var a=document;var b="SCRIPT";"application/xhtml+xml"===a.contentType&&(b=b.toLowerCase());b=a.createElement(b);a=null===c?"null":void 0===c?"undefined":c;if(void 0===g){var d=null;var l=e.trustedTypes;if(l&&l.createPolicy){try{d=l.createPolicy("goog#html",{createHTML:f,createScript:f,createScriptURL:f})}catch(r){e.console&&e.console.error(r.message)}g=
d}else g=d}a=(d=g)?d.createScriptURL(a):a;a=new k(a,m);b.src=a instanceof k&&a.constructor===k?a.g:"type_error:TrustedResourceUrl";var h,p;(h=(a=null==(p=(h=(b.ownerDocument&&b.ownerDocument.defaultView||window).document).querySelector)?void 0:p.call(h,"script[nonce]"))?a.nonce||a.getAttribute("nonce")||"":"")&&b.setAttribute("nonce",h);document.body.appendChild(b);google.psa=!0;google.lx=n};google.bx||google.lx()};google.xjsu=u;e._F_jsUrl=u;setTimeout(function(){0<amd?google.caft(function(){return q()},amd):q()},0);})();window._ = window._ || {};window._DumpException = _._DumpException = function(e){throw e;};window._s = window._s || {};_s._DumpException = _._DumpException;window._qs = window._qs || {};_qs._DumpException = _._DumpException;function _F_installCss(c){}
(function(){google.jl={blt:'none',chnk:0,dw:false,dwu:true,emtn:0,end:0,ico:false,ikb:0,ine:false,injs:'none',injt:0,injth:0,injv2:false,lls:'default',pdt:0,rep:0,snet:true,strt:0,ubm:false,uwp:true};})();(function(){var pmc='{\\x22d\\x22:{},\\x22sb_he\\x22:{\\x22agen\\x22:true,\\x22cgen\\x22:true,\\x22client\\x22:\\x22heirloom-hp\\x22,\\x22dh\\x22:true,\\x22ds\\x22:\\x22\\x22,\\x22fl\\x22:true,\\x22host\\x22:\\x22google.com\\x22,\\x22jsonp\\x22:true,\\x22msgs\\x22:{\\x22cibl\\x22:\\x22Clear Search\\x22,\\x22dym\\x22:\\x22Did you mean:\\x22,\\x22lcky\\x22:\\x22I\\\\u0026#39;m Feeling Lucky\\x22,\\x22lml\\x22:\\x22Learn more\\x22,\\x22psrc\\x22:\\x22This search was removed from your \\\\u003Ca href\\x3d\\\\\\x22/history\\\\\\x22\\\\u003EWeb History\\\\u003C/a\\\\u003E\\x22,\\x22psrl\\x22:\\x22Remove\\x22,\\x22sbit\\x22:\\x22Search by image\\x22,\\x22srch\\x22:\\x22Google Search\\x22},\\x22ovr\\x22:{},\\x22pq\\x22:\\x22\\x22,\\x22rfs\\x22:[],\\x22sbas\\x22:\\x220 3px 8px 0 rgba(0,0,0,0.2),0 0 0 1px rgba(0,0,0,0.08)\\x22,\\x22stok\\x22:\\x22PzLWq_ePb0mzRHJ0XTrvTz5mDfs\\x22}}';google.pmc=JSON.parse(pmc);})();(function(){
var b=function(a){var c=0;return function(){return c<a.length?{done:!1,value:a[c++]}:{done:!0}}},e=this||self;var g,h;a:{for(var k=["CLOSURE_FLAGS"],l=e,n=0;n<k.length;n++)if(l=l[k[n]],null==l){h=null;break a}h=l}var p=h&&h[610401301];g=null!=p?p:!1;var q,r=e.navigator;q=r?r.userAgentData||null:null;function t(a){return g?q?q.brands.some(function(c){return(c=c.brand)&&-1!=c.indexOf(a)}):!1:!1}function u(a){var c;a:{if(c=e.navigator)if(c=c.userAgent)break a;c=""}return-1!=c.indexOf(a)};function v(){return g?!!q&&0<q.brands.length:!1}function w(){return u("Safari")&&!(x()||(v()?0:u("Coast"))||(v()?0:u("Opera"))||(v()?0:u("Edge"))||(v()?t("Microsoft Edge"):u("Edg/"))||(v()?t("Opera"):u("OPR"))||u("Firefox")||u("FxiOS")||u("Silk")||u("Android"))}function x(){return v()?t("Chromium"):(u("Chrome")||u("CriOS"))&&!(v()?0:u("Edge"))||u("Silk")}function y(){return u("Android")&&!(x()||u("Firefox")||u("FxiOS")||(v()?0:u("Opera"))||u("Silk"))};var z=v()?!1:u("Trident")||u("MSIE");y();x();w();var A=!z&&!w(),D=function(a){if(/-[a-z]/.test("ved"))return null;if(A&&a.dataset){if(y()&&!("ved"in a.dataset))return null;a=a.dataset.ved;return void 0===a?null:a}return a.getAttribute("data-"+"ved".replace(/([A-Z])/g,"-$1").toLowerCase())};var E=[],F=null;function G(a){a=a.target;var c=performance.now(),f=[],H=f.concat,d=E;if(!(d instanceof Array)){var m="undefined"!=typeof Symbol&&Symbol.iterator&&d[Symbol.iterator];if(m)d=m.call(d);else if("number"==typeof d.length)d={next:b(d)};else throw Error("a`"+String(d));for(var B=[];!(m=d.next()).done;)B.push(m.value);d=B}E=H.call(f,d,[c]);if(a&&a instanceof HTMLElement)if(a===F){if(c=4<=E.length)c=5>(E[E.length-1]-E[E.length-4])/1E3;if(c){c=google.getEI(a);a.hasAttribute("data-ved")?f=a?D(a)||"":"":f=(f=
a.closest("[data-ved]"))?D(f)||"":"";f=f||"";if(a.hasAttribute("jsname"))a=a.getAttribute("jsname");else{var C;a=null==(C=a.closest("[jsname]"))?void 0:C.getAttribute("jsname")}google.log("rcm","&ei="+c+"&ved="+f+"&jsname="+(a||""))}}else F=a,E=[c]}window.document.addEventListener("DOMContentLoaded",function(){document.body.addEventListener("click",G)});}).call(this);</script></body></html>
0

""";

        SessionInputBufferImpl buf = new SessionInputBufferImpl(65536);
        //buf.fillBuffer(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));

        InputStream is = new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
        ClassicHttpResponse result = parser.parse(buf, is);

        receiveResponseEntity(result, buf, is, http1Config);

        System.out.println(result);

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        result.getEntity().writeTo(body);

        String bodyString = body.toString(StandardCharsets.UTF_8);

        System.out.println(bodyString);
    }

    public void receiveResponseEntity( final ClassicHttpResponse response, SessionInputBuffer inBuffer, InputStream is, Http1Config http1Config) throws HttpException, IOException
    {
        final long len = contentLengthStrategy.determineLength(response);
        response.setEntity(createIncomingEntity(response, inBuffer, is, len, http1Config));
    }

    HttpEntity createIncomingEntity(
            final HttpMessage message,
            final SessionInputBuffer inBuffer,
            final InputStream inputStream,
            final long len, Http1Config http1Config) {
        return new IncomingHttpEntity(
                createContentInputStream(len, inBuffer, inputStream, http1Config),
                len >= 0 ? len : -1, len == ContentLengthStrategy.CHUNKED,
                message.getFirstHeader(HttpHeaders.CONTENT_TYPE),
                message.getFirstHeader(HttpHeaders.CONTENT_ENCODING));
    }

    private InputStream createContentInputStream(
            final long len,
            final SessionInputBuffer buffer,
            final InputStream inputStream,
            Http1Config http1Config) {
        if (len > 0) {
            return new ContentLengthInputStream(buffer, inputStream, len);
        } else if (len == 0) {
            return EmptyInputStream.INSTANCE;
        } else if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkedInputStream(buffer, inputStream, http1Config);
        } else {
            return new IdentityInputStream(buffer, inputStream);
        }
    }


    static class IncomingHttpEntity implements HttpEntity {

        private final InputStream content;
        private final long len;
        private final boolean chunked;
        private final Header contentType;
        private final Header contentEncoding;

        IncomingHttpEntity(final InputStream content, final long len, final boolean chunked, final Header contentType, final Header contentEncoding) {
            this.content = content;
            this.len = len;
            this.chunked = chunked;
            this.contentType = contentType;
            this.contentEncoding = contentEncoding;
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public boolean isChunked() {
            return chunked;
        }

        @Override
        public long getContentLength() {
            return len;
        }

        @Override
        public String getContentType() {
            return contentType != null ? contentType.getValue() : null;
        }

        @Override
        public String getContentEncoding() {
            return contentEncoding != null ? contentEncoding.getValue() : null;
        }

        @Override
        public InputStream getContent() throws IOException, IllegalStateException {
            return content;
        }

        @Override
        public boolean isStreaming() {
            return content != null && content != EmptyInputStream.INSTANCE;
        }

        @Override
        public void writeTo(final OutputStream outStream) throws IOException {
            AbstractHttpEntity.writeTo(this, outStream);
        }

        @Override
        public Supplier<List<? extends Header>> getTrailers() {
            return null;
        }

        @Override
        public Set<String> getTrailerNames() {
            return Collections.emptySet();
        }

        @Override
        public void close() throws IOException {
            Closer.close(content);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append('[');
            sb.append("Content-Type: ");
            sb.append(getContentType());
            sb.append(',');
            sb.append("Content-Encoding: ");
            sb.append(getContentEncoding());
            sb.append(',');
            final long len = getContentLength();
            if (len >= 0) {
                sb.append("Content-Length: ");
                sb.append(len);
                sb.append(',');
            }
            sb.append("Chunked: ");
            sb.append(isChunked());
            sb.append(']');
            return sb.toString();
        }

    }
}
