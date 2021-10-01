var debugEnabled = true;
var isUseBootstrapUI = false;

var serverUrl = "http://localhost:9080/";

function getXMLNode(xml)
{
	return $.parseXML(xml);
}

function xmlNodeToXML(xmln)
{
	if (window.ActiveXObject) {
		var str = xmln.xml;
		return str;
	 }
	// code for Mozilla, Firefox, Opera, etc.
	else {
	   var str = (new XMLSerializer()).serializeToString(xmln);
	   return str;
	}
}

function formatXml(xml) {
    var formatted = '';
    var reg = /(>)(<)(\/*)/g;
    xml = xml.replace(reg, '$1\r\n$2$3');
    var pad = 0;
    jQuery.each(xml.split('\r\n'), function(index, node) {
        var indent = 0;
        if (node.match( /.+<\/\w[^>]*>$/ )) {
            indent = 0;
        } else if (node.match( /^<\/\w/ )) {
            if (pad != 0) {
                pad -= 1;
            }
        } else if (node.match( /^<\w[^>]*[^\/]>.*$/ )) {
            indent = 1;
        } else {
            indent = 0;
        }
 
        var padding = '';
        for (var i = 0; i < pad; i++) {
            padding += '  ';
        }
 
        formatted += padding + node + '\r\n';
        pad += indent;
    });
 
    return formatted;
}

$.fn.serializeObject = function()
{
	var o = {};
	var or = {};
    var a = this.serializeArray();
    $.each(a, function() {
		var element = document.getElementById(this.name);
    	if (o[this.name] !== undefined) {		    		 
            if (!o[this.name].push) {
                o[this.name] = [o[this.name]];
            }
			var val = this.value || '';
			if(element!=null && element.hasOwnProperty('data') && element.data != undefined)
			{
				val = element.data;
				o[this.name].push(val);
			}
			else if(val!='')
	            o[this.name].push(val);
        } else {
        	if(this.name.indexOf("[")==-1)
			{
				var val = this.value || '';
				if(element!=null && element.hasOwnProperty('data') && element.data != undefined)
				{
					val = element.data;
					o[this.name] = val;
				}
				else if(val!='')
					o[this.name] = val;
			}
        	else
        	{		        		
        		var temp = this.name.replace(/]/g,"");
        		//temp = temp.replace("]","");
        		temp = temp.split("[");
				/*var yemp = [];
				var yyemp = "";
				for(var jk=0;jk<temp.length;jk++)
				{
					if(temp[jk].length>0 && temp[jk].charAt(temp[jk].length-1)=='\\')
					{
						yyemp += "[" + temp[jk];
					}
					else
					{
						yemp[counter++] = yyemp;
						yyemp = "";
					}
				}
				temp = yemp;*/
        		if(temp.length>=2)
        		{	
					var t;
					var asignedVal = false;
					for(var ii=1;ii<temp.length;ii++)
					{
						if(temp[ii].indexOf("'")==0 && temp[ii].length>2)
						{
							if(!asignedVal)
							{
								if(o[temp[0]] == undefined)
								{
									o[temp[0]] = {};
								}
								t = o[temp[0]];
								asignedVal = true;
							}
							temp[ii] = temp[ii].substr(1, temp[ii].length-2);
							if(ii==temp.length-1)
							{
								var val = this.value || '';
								if(element!=null && element.hasOwnProperty('data') && element.data != undefined)
								{
									val = element.data;
									t[temp[ii]] = val;
								}
								else if(val!='')
									t[temp[ii]] = val;
							}
							else
							{
								if(t[temp[ii]] == undefined)
								{
									if(temp.length>(ii+1) && temp[ii+1].indexOf("'")==0 && temp[ii+1].length>2)
										t[temp[ii]] ={};
									else
										t[temp[ii]] = [];
								}
								t = t[temp[ii]];
							}
						}
						else
						{
							if(!asignedVal)
							{
								if(o[temp[0]] == undefined)
								{
									o[temp[0]] = [];
								}
								t = o[temp[0]];
								asignedVal = true;								
							}
							/*if (!(t instanceof Array)) {
								t = [];
							}*/
							if(ii==temp.length-1 && !isNaN(temp[ii]))
							{
								var val = this.value || '';								
								if(element!=null && element.hasOwnProperty('data') && element.data != undefined)
								{
									val = element.data;
									t.splice(temp[ii]*1, 0, val);
								}
								else if(val!='')
									t.splice(temp[ii]*1, 0, val);
							}
							else
							{
								if(t[temp[ii]] == undefined)
								{
									if(temp.length>(ii+1) && temp[ii+1].indexOf("'")==0 && temp[ii+1].length>2)
										t[temp[ii]] ={};
									else
										t[temp[ii]] = [];
									//t[temp[ii]] = {};
								}
								t = t[temp[ii]];
							}
						}
					}
        		}
        		else
        		{
					var val = this.value || '';
					if(element!=null && element.hasOwnProperty('data') && element.data != undefined)
					{
						val = element.data;
						o[this.name] = val;
					}
					else if(val!='')
						o[this.name] = val;
				}
        	}
        }
    });
	neutralizeNullProperties(o);
	neutralizeJSONMaps(o, null);
    return o;
};

function neutralizeJSONMaps(o, pkey)
{
	if(Object.prototype.toString.call(o) === '[object Array]') {
		for(var i=0;i<o.length;i++) {
			if(pkey==null)
				pkey = '';
			neutralizeJSONMaps(o[i], pkey+'['+i+']');
		}
	} else if(Object.prototype.toString.call(o) === '[object Object]') {
		for(var key in o){
			var value = o[key];
			var hkey;
			if(pkey!=null)
				hkey = pkey + "['" + key + "']";
			else
				hkey = key;
			if(schemaMap[hkey+"_dummymap"]!=undefined && schemaMap[hkey+"_dummymap"]['mtype']!=undefined
				&& schemaMap[hkey+"_dummymap"]['mtype']=='map' && (schemaMap[hkey+"_dummymap"]['type']=='array' || schemaMap[hkey+"_dummymap"]['type']=='marray')
				&& Object.prototype.toString.call(value) === '[object Array]')
			{
				var newValue = {};
				for(var i=0;i<value.length;i++) {
					neutralizeJSONMaps(value[i], hkey+'['+i+']');
					newValue[value[i].key] = value[i].value;
				}
				o[key] = newValue;
			}
			else
			{
				neutralizeJSONMaps(value, hkey);
			}
		}
	}
}

function replaceAll(txt, replace, with_this) {
  return txt.replace(new RegExp(replace, 'g'),with_this);
}

function neutralizeNullProperties(o)
{
	if(typeof o != 'object')return true;
	var flag = false;
	for(var property in o)
	{
		if(o.hasOwnProperty(property) && o[property]!=null)
		{			
			if(!neutralizeNullProperties(o[property]))
			{
				delete o[property];
			} 
			else
			{
				flag = true;
			} 
		}
	}
	return flag;
}

$.fn.serializeObjectToXML = function()
{
	var o = undefined;
	var or = document.createElement("_tempor");
    var a = this.serializeArray();
    $.each(a, function() {
		var element = document.getElementsByName(this.name)[0];
    	var temp = this.name.replace(/]/g,"");
		temp = temp.split("[");
		if (o !== undefined)
		{}
		else
			o = document.createElement(temp[0]);
		
		if(temp.length>=2)
		{	
			var t = o;
			var asignedVal = false;
			for(var ii=1;ii<temp.length;ii++)
			{
				if(temp[ii].indexOf("'")==0 && temp[ii].length>2)
				{							
					temp[ii] = temp[ii].substr(1, temp[ii].length-2);
					if(ii==temp.length-1)
					{
						var val = this.value || '';
						if(element!=null && element.hasOwnProperty('data') && element.data != undefined)
						{
							val = element.data;
						}
						else if(element!=null && element.getAttribute('isattr')=="true")
						{
							t.setAttribute(temp[ii], val);
							val = '';
						}
						if(val!='')
						{
							var n = document.createElement(temp[ii]);
							n.innerHTML = val;
							t.appendChild(n);
							t = n;
						}
					}
					else
					{
						if(t.getElementsByTagName(temp[ii]).length==0)
						{
							if(temp.length>(ii+1) && temp[ii+1].indexOf("'")==0 && temp[ii+1].length>2)
							{
								var n = document.createElement(temp[ii]);
								t.appendChild(n);
								t = n;
							}
							else
							{
								var n = document.createElement(temp[ii]);
								t.appendChild(n);
								t = n;
							}
						}
						else
							t = t.getElementsByTagName(temp[ii])[0];
					}
				}
				else
				{
					if(ii==temp.length-1)
					{
						var val = this.value || '';
						if(element!=null && element.hasOwnProperty('data') && element.data != undefined)
						{
							val = element.data;
						}
						else if(element!=null && element.getAttribute('isattr')=="true")
						{
							t.setAttribute(temp[ii], val);
							val = '';
						}
						if(val!='')
						{
							if(!isNumber(temp[ii]))
							{
								var n = document.createElement(temp[ii]);
								t.appendChild(n);
								t = n;
							}
							else
								t.innerHTML += val+" ";
						}
					}
					else
					{
						if(t.getElementsByTagName(temp[ii]).length==0)
						{
							if(temp.length>(ii+1) && temp[ii+1].indexOf("'")==0 && temp[ii+1].length>2)
							{
								if(!isNumber(temp[ii]))
								{
									var n = document.createElement(temp[ii]);
									t.appendChild(n);
									t = n;
								}
								else
									t.innerHTML += val+" ";
							}
							else
							{
								if(!isNumber(temp[ii]))
								{
									var n = document.createElement(temp[ii]);
									t.appendChild(n);
									t = n;
								}
								else
									t.innerHTML += val+" ";
							}
						}
						else
							t = t.getElementsByTagName(temp[ii])[0];
					}
				}
			}
		}
		else
		{
			var val = this.value || '';
			if(element!=null && element.hasOwnProperty('data') && element.data != undefined)
			{
				val = element.data;
			}
			else if(element!=null && element.getAttribute('isattr')=="true")
			{
				o.setAttribute(this.name, val);
				val = '';
			}
			if(val!='')
			{
				o.innerHTML = val;
			}
		}
    });
	or.appendChild(o);
    return or.innerHTML;
};


$.fn.serializeObjectToStrictXML = function(xmlNode)
{
	var a = this.serializeArray();
    $.each(a, function() {
		var element = document.getElementsByName(this.name)[0];
    	var temp = this.name.replace(/]/g,"");
		temp = temp.split("[");
		
		var xpath = "";
		var val = this.value || '';
		if(element!=null && element.getAttribute('data')!= undefined)
		{
			val = element.data;
		}
		var isAttr = false;
		if(element!=null && element.getAttribute('isattr')=="true")
		{
			isAttr = true;
		}
		var ndIndex = -1;
		var currNode = xmlNode;
		for(var i=0;i<temp.length;i++)
		{
			if(temp[i].indexOf("'")==0 && temp[i].length>2)
			{							
				temp[i] = temp[i].substr(1, temp[i].length-2);
			}
			if(isNumber(temp[i]) && i!=temp.length-1)
			{
				currNode = findXMLNodeByXpath(currNode, xpath, temp[i]*1);
				xpath = "";
			}
			else if(i==temp.length-1)
			{
				if(isNumber(temp[i]))
					ndIndex = temp[i]*1;
				else if(isAttr)
					xpath += "//@" + temp[i];
				else
					xpath += "//" + temp[i];
			}
			else
			{
				xpath += "//" + temp[i];
			}
		}
		//alert(xpath);
		updateXMLNodeNew(currNode, xpath, val, ndIndex);
	});
	//alert(xmlNodeToXML(xmlNode));	
    return xmlNodeToXML(xmlNode);
};

function updateXMLNodeNew(xmlNode, xpath, value, index)
{
	if(value==null || value==undefined)
		return;
	var nodes = document.evaluate(xpath, xmlNode, null, 7, null);
	if(nodes.snapshotLength!=0) {
		if(xpath.indexOf('@')!=-1)
			nodes.snapshotItem(0).value = value;
		else if(index==-1 || index==null || index==undefined || index=="")
			nodes.snapshotItem(0).innerHTML = value;
		else if(index<nodes.snapshotLength)
			nodes.snapshotItem(index).innerHTML = value;
		else if(index==nodes.snapshotLength)
		{
			var nindex = nodes.snapshotLength-1;
			nodes.snapshotItem(nindex).parentNode.appendChild(nodes.snapshotItem(0).cloneNode());
			updateXMLNodeNew(xmlNode, xpath, value, index);
		}
	}
}

function findXMLNodeByXpath(xmlNode, xpath, index)
{
	var nodes = document.evaluate(xpath, xmlNode, null, 7, null);
	if(nodes.snapshotLength!=0) {
		if(index<=nodes.snapshotLength-1)
			return nodes.snapshotItem(index);
		else if(index==nodes.snapshotLength)
		{
			var nindex = nodes.snapshotLength-1;
			var newnode = nodes.snapshotItem(0).cloneNode();
			newnode.innerHTML = nodes.snapshotItem(nindex).innerHTML;
			nodes.snapshotItem(nindex).parentNode.appendChild(newnode);
			return newnode;
		}
	}
	return xmlNode;
}

function findXMLNodeValueByXpath(xml, path) {
    var nodes = document.evaluate(path, getXMLNode(xml), null, 7, null);
	if(nodes.snapshotLength!=0) {
		return nodes.snapshotItem(0).innerHTML;
	}
}

function replaceAll(txt, replace, with_this) {
  return txt.replace(new RegExp(replace, 'g'),with_this);
}

$.fn.serializeObjectToMap = function()
{
	var o = new Array();
	var map = this.serializeObject();
	for (var mkey in map) {
		o.push({'key': mkey, 'value': map[mkey]});
	}
	return o;
};

function shortHandTableHeaders(tableID, limit, wh) {

    var ths = $('#' + tableID + ' tbody tr '+wh);        
    var content;
    
    ths.each (function () {
        var $this = $(this);
        
        content = $this.text();

        if (content.length > limit) {
           $this.data('longheader', content);
           $this.text (shortHandHeaderTxt(content, limit));
            
           $this.hover (
               function() {
                   $(this).text($this.data('longheader'));
               },
               function () {
                   $(this).text(shortHandHeaderTxt($this.data('longheader'), limit));
               }
           );
         }

    });
    
}

function shortHandHeaderTxt(txt, limit) {
	return txt.substring(0, limit - 2) + "..";
}
		
function addTableRow(tableID) 
{			 
	var table = document.getElementById(tableID);
	var rowCount = table.rows.length;
	var row = table.insertRow(rowCount);
	var cell1 = row.insertCell(0);
	var element1 = document.createElement("input");
	element1.type = "checkbox";
    cell1.appendChild(element1);
 
    var cell2 = row.insertCell(1);
    cell2.innerHTML = rowCount + 1;
 
    var cell3 = row.insertCell(2);
    var element2 = document.createElement("input");
    element2.type = "text";
    cell3.appendChild(element2); 
}

function syntaxHighlight(json) {
    json = json.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    return json.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, function (match) {
        var cls = 'number';
        if (/^"/.test(match)) {
            if (/:$/.test(match)) {
                cls = 'key';
            } else {
                cls = 'string';
            }
        } else if (/true|false/.test(match)) {
            cls = 'boolean';
        } else if (/null/.test(match)) {
            cls = 'null';
        }
        return '<span class="' + cls + '">' + match + '</span>';
    });
}

function createCookie(name,value,days) {
	if (days) {
		var date = new Date();
		date.setTime(date.getTime()+(days*24*60*60*1000));
		var expires = "; expires="+date.toGMTString();
	}
	else var expires = "";
	document.cookie = name+"="+value+expires+"; path=/";
	alert("URL saved");
}

function readCookie(name) {
	var nameEQ = name + "=";
	var ca = document.cookie.split(';');
	for(var i=0;i < ca.length;i++) {
		var c = ca[i];
		while (c.charAt(0)==' ') c = c.substring(1,c.length);
		if (c.indexOf(nameEQ) == 0) return c.substring(nameEQ.length,c.length);
	}
	return null;
}

function eraseCookie(name) {
	createCookie(name,"",-1);
}

function escapeHtml(s) {
	return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function printResponse(msg, jqXhr, contentTypeHeader)
{
	var resp = jqXhr.responseText;
	if(contentTypeHeader!=null && contentTypeHeader.indexOf('application/json')!=-1)
	{
		if(jqXhr.responseText!=null)
		{
			eval('var ty = '+jqXhr.responseText+';');
			resp = syntaxHighlight(JSON.stringify(ty, undefined, 4));
		}
		else
		{
			 resp = syntaxHighlight(JSON.stringify(msg, undefined, 4));
		}
		
	}
    else if(contentTypeHeader!=null && contentTypeHeader.indexOf('xml')!=-1)
	{
		//resp = formatXml(resp);
		resp = vkbeautify.xml(resp);
		resp = escapeHtml(resp);
		//resp = "<textarea rows=\"20\" cols=\"40\" style=\"border:none;width:100%\" readonly>"+resp+"</textarea>";
	}
    
	if(contentTypeHeader!=null && contentTypeHeader.indexOf('xml')!=-1)
		resp = prettyPrintOne(resp);

	$('#status').html(resp);
}

function executeTest(urlid,meth,contType,formName,succFunc,failFunc)
{
	if(debugEnabled)alert('Start Execute Test method call...');
	var testUrl = $(urlid).val();
	var url = "../" + testUrl;

	var paramsPresent = false;
	var alertStr = "";
	var ttestUrl = testUrl;
	var isAuthTokNmPresentInUrl = false;
	while(ttestUrl.indexOf("{")!=-1 && ttestUrl.indexOf("}")!=-1)
	{				
		var temm = ttestUrl.substr(ttestUrl.indexOf("{")+1, ttestUrl.indexOf("}")-ttestUrl.indexOf("{")-1);
		ttestUrl = ttestUrl.substr(ttestUrl.indexOf("}")+1);
		
		if(typeof authTokNm!="undefined" && typeof authTokTyp!="undefined" && temm==authTokNm)
		{
			isAuthTokNmPresentInUrl = true;
		}
		else
		{
			alertStr += temm + "\n";
			paramsPresent = true;
		}
	}
	if(paramsPresent)
	{
		alertStr = "Would you like to update the following parameter values in the URL,\n" + alertStr;
	}

	if(alertStr!='' && confirm(alertStr))
	{
		return;
	}
	
	$(formName).attr('action', url);
		
	var content = "";
	var vheaders = {};
	$('.headerparam').each(function() {
		vheaders[this.id] = this.value;
	});

	if(typeof authTokNm!="undefined" && typeof authTokTyp!="undefined")
	{
		var currUrl = document.location + "";
		var currToken = "";
		if(currUrl.indexOf("?")!=-1 && currUrl.indexOf(authTokNm+"=")!=-1)
		{
			currToken = currUrl.substr(currUrl.indexOf("?")+1);
			currToken = currToken.substr(currToken.indexOf("=")+1);
			if(currToken.indexOf("&")!=-1)
				currToken = currToken.substr(0, currToken.indexOf("&"));
			if(debugEnabled)alert(authTokNm+"="+currToken);
		}
		if(authTokTyp=="queryparam")
		{
			if(isAuthTokNmPresentInUrl)
			{
				url = url.replace("{"+authTokNm+"}", currToken);
			}
			else if(url.indexOf('?')==-1)
			{
				url += "?" + authTokNm + "=" + currToken;
			}
			else
			{
				if(url.charAt(url.length-1)=='&')
					url += authTokNm + "=" + currToken;
				else
					url += "&" + authTokNm + "=" + currToken;
			}
			$(formName).attr('action', url);
		}
		else if(authTokTyp=="header")
		{
			vheaders[authTokNm] = currToken;
		}
		else if(authTokTyp=="postparam")
		{
			$(formName).append('<input type="hidden" name="'+authTokNm+'" value="'+currToken+'"/>');
		}
	}

	if($('#raw_req_cont_flag').is(':checked'))
	{
		contType = $('#raw_req_cont_type').val();		
	}
	if($('#req-txtarea').length>0)
	{
		content = $('#req-txtarea').val();
	}
	if(contType.indexOf('json')!=-1 || contType.indexOf('text/plain')!=-1) 
	{
		var processDataflag = false;
		if($('#raw_req_cont_flag').is(':checked') || (contType.indexOf('text/plain')!=-1 && $('#req-txtarea').length>0))
			content = $('#req-txtarea').val();
		else if(contType.indexOf('application/json')!=-1)
			content = JSON.stringify($(formName).serializeObject());
		else if(contType.indexOf('application/xml')!=-1 && window['requestXMLNode'] != undefined)
			content = $(formName).serializeObjectToStrictXML(requestXMLNode);
		else if(contType.indexOf('application/x-www-form-urlencoded')!=-1)
		{
			processDataflag = true;
			content = $(formName).serializeArray()
		}

		if(debugEnabled)alert("Request: "+content);
		$.blockUI({ message: '<h3><img src="resources/busy.gif" /> Just a moment...</h3>' }); 
		var start = new Date().getTime();
		$.ajax({
		  headers: vheaders,
		  type: meth,
		  processData: processDataflag,
		  url: url,
		  contentType: contType,
		  data: content
		}).done(function(msg,statusText,jqXhr) {
		  $.unblockUI();
		  var end = new Date().getTime();
		  var time = end - start;
		  $('#restime').html(time+" ms");
		  var reshdrdata = "Status: " + jqXhr.status + "\n";
		  reshdrdata += jqXhr.getAllResponseHeaders();
		  $('#reshdrs').html(reshdrdata);
		  var reshdrdata = "Status: " + jqXhr.status + "<br/>";
		  reshdrdata += jqXhr.getAllResponseHeaders();
		  var contentTypeHeader = jqXhr.getResponseHeader('Content-Type');
		  if(contentTypeHeader==null)
			 contentTypeHeader = jqXhr.getResponseHeader('Content-type');
		  if(contentTypeHeader==null)
			 contentTypeHeader = jqXhr.getResponseHeader('content-type');
		  if(contentTypeHeader==null)
			 contentTypeHeader = jqXhr.getResponseHeader('content-Type');
		  if(debugEnabled)alert("Completed Execute Test method call "+jqXhr.status+" "+statusText);
		  if(formName=="#Login" && typeof loginExtractionNm!="undefined" && typeof loginExtractionTyp!="undefined")
		  {
			  if(loginExtractionTyp=="json")
			  {
				  eval("var token = msg."+loginExtractionNm+";");
			  }
			  else if(loginExtractionTyp=="plain")
			  {
				  eval("var token = '"+jqXhr.responseText+"';");
			  }
			  else if(loginExtractionTyp=="header")
			  {
				  eval("var token = '"+jqXhr.getResponseHeader(loginExtractionNm)+"';");
			  }
			  else if(loginExtractionTyp=="cookie")
			  {
				  //eval("var token = '"+$.cookie(loginExtractionNm, {path: '/'})+"';");
			  }
			  else if(loginExtractionTyp=="xml")
			  {
				  if(loginExtractionNm.indexOf("//")==-1)
					loginExtractionNm = loginExtractionNm.replace(/./g, "//");	
				  if(loginExtractionNm.charAt(0)!='/')
					loginExtractionNm = "//"+loginExtractionNm;
				  
				  var nodeval = findXMLNodeValueByXpath(jqXhr.responseText, loginExtractionNm);
				  if(nodeval!=undefined && nodeval!="") {
				  	alert(nodeval);
					eval("var token = '"+nodeval+"';");
				  }
			  }
			  if(loginExtractionTyp=="header" || loginExtractionTyp=="plain")
			  {
				  $('#status').html(loginExtractionNm + " = " + token);
			  }
			  else
			  {
				  printResponse(msg, jqXhr, contentTypeHeader);
			  }
			  if(typeof token!="undefined")
				 handleAuth(token);		 
		  }
		  else
		  {
			  printResponse(msg, jqXhr, contentTypeHeader);
		  }
		  if(!isUseBootstrapUI)
			adjustHeight();
		   if(succFunc!=null)succFunc(jqXhr);			  
		}).fail(function(jqXhr, textStatus, msg) {
		  $.unblockUI();
		  var end = new Date().getTime();
		  var time = end - start;
		  $('#restime').html(time+" ms");
		  var reshdrdata = "Status: " + jqXhr.status + "\n";
		  reshdrdata += jqXhr.getAllResponseHeaders();
		  $('#reshdrs').html(reshdrdata);
		  var contentTypeHeader = jqXhr.getResponseHeader('Content-Type');
		  if(contentTypeHeader==null)
			 contentTypeHeader = jqXhr.getResponseHeader('Content-type');
		  if(contentTypeHeader==null)
			 contentTypeHeader = jqXhr.getResponseHeader('content-type');
		  if(contentTypeHeader==null)
			 contentTypeHeader = jqXhr.getResponseHeader('content-Type');
		  if(debugEnabled)alert("Completed Fail Execute Test method call "+jqXhr.status+" "+msg);
		  printResponse(msg, jqXhr, contentTypeHeader);
		  if(!isUseBootstrapUI)
			adjustHeight();
		  if(failFunc!=null)failFunc(jqXhr);
		});
	}
	else
	{		
		if(meth.toUpperCase()=="GET")
		{
			$.blockUI({ message: '<h3><img src="resources/busy.gif" /> Just a moment...</h3>' }); 
			var start = new Date().getTime();
			$.ajax({
			  headers: vheaders,
			  type: meth,
			  processData: false,
			  url: url
			}).done(function(msg,statusText,jqXhr) {
			  $.unblockUI();
			  var end = new Date().getTime();
			  var time = end - start;
		      $('#restime').html(time+" ms");
			  var reshdrdata = "Status: " + jqXhr.status + "\n";
			  reshdrdata += jqXhr.getAllResponseHeaders();
			  $('#reshdrs').html(reshdrdata);
			  var contentTypeHeader = jqXhr.getResponseHeader('Content-Type');
			  if(contentTypeHeader==null)
				 contentTypeHeader = jqXhr.getResponseHeader('Content-type');
			  if(contentTypeHeader==null)
				 contentTypeHeader = jqXhr.getResponseHeader('content-type');
			  if(contentTypeHeader==null)
				 contentTypeHeader = jqXhr.getResponseHeader('content-Type');
			  if(debugEnabled)alert("Completed Execute Test method call "+jqXhr.status+" "+statusText);
			  if(formName=="#Login" && typeof loginExtractionNm!="undefined" && typeof loginExtractionTyp!="undefined")
			  {
				  if(loginExtractionTyp=="json")
				  {
					  eval("var token = msg."+loginExtractionNm+";");
				  }
				  else if(loginExtractionTyp=="plain")
				  {
					  eval("var token = '"+jqXhr.responseText+"';");
				  }
				  else if(loginExtractionTyp=="header")
				  {
					  eval("var token = '"+jqXhr.getResponseHeader(loginExtractionNm)+"';");
				  }
				  else if(loginExtractionTyp=="cookie")
				  {
					  //eval("var token = '"+$.cookie(loginExtractionNm, {path: '/'})+"';");
				  }
				  else if(loginExtractionTyp=="xml")
				  {
					  if(loginExtractionNm.indexOf("//")==-1)
						loginExtractionNm = loginExtractionNm.replace(/./g, "//");	
					  if(loginExtractionNm.charAt(0)!='/')
						loginExtractionNm = "//"+loginExtractionNm;
					  
					  var nodeval = findXMLNodeValueByXpath(jqXhr.responseText, loginExtractionNm);
					  if(nodeval!=undefined && nodeval!="") {
						alert(nodeval);
						eval("var token = '"+nodeval+"';");
					  }
				  }
				  if(loginExtractionTyp=="header" || loginExtractionTyp=="plain")
				  {
					  $('#status').html(loginExtractionNm + " = " + token);
				  }
				  else 
				  {
					  printResponse(msg, jqXhr, contentTypeHeader);
				  }
				  if(typeof token!="undefined")
					 handleAuth(token);
			  }
			  else
			  {
				  printResponse(msg, jqXhr, contentTypeHeader);
			  }
			  if(!isUseBootstrapUI)
				  adjustHeight();
			  if(succFunc!=null)succFunc(jqXhr);
			}).fail(function(jqXhr, textStatus, msg) {
              $.unblockUI();
			  var end = new Date().getTime();
		      var time = end - start;
		      $('#restime').html(time+" ms");
			  var reshdrdata = "Status: " + jqXhr.status + "\n";
			  reshdrdata += jqXhr.getAllResponseHeaders();
			  $('#reshdrs').html(reshdrdata);
			  var contentTypeHeader = jqXhr.getResponseHeader('Content-Type');
			  if(contentTypeHeader==null)
				 contentTypeHeader = jqXhr.getResponseHeader('Content-type');
			  if(contentTypeHeader==null)
				 contentTypeHeader = jqXhr.getResponseHeader('content-type');
			  if(contentTypeHeader==null)
				 contentTypeHeader = jqXhr.getResponseHeader('content-Type');
			  if(debugEnabled)alert("Completed Fail Execute Test method call "+jqXhr.status+" "+msg);
			  printResponse(msg, jqXhr, contentTypeHeader);
			  if(!isUseBootstrapUI)
				  adjustHeight();
			  if(failFunc!=null)failFunc(jqXhr);
			});
		}
		else
		{
			var processDataflag = false;
			if($('#raw_req_cont_flag').is(':checked') || (contType.indexOf('text/plain')!=-1 && $('#req-txtarea').length>0))
				content = $('#req-txtarea').val();
			else if(contType.indexOf('application/json')!=-1)
				content = JSON.stringify($(formName).serializeObject());
			else if(contType.indexOf('application/xml')!=-1 && window['requestXMLNode'] != undefined)
				content = $(formName).serializeObjectToStrictXML(requestXMLNode);
			else if(contType.indexOf('application/x-www-form-urlencoded')!=-1)
			{
				processDataflag = true;
				content = $(formName).serializeArray()
			}

			if(debugEnabled)alert("Request: "+$(formName).serialize());
			$.blockUI({ message: '<h3><img src="resources/busy.gif" /> Just a moment...</h3>' }); 
			var start = new Date().getTime();
			$.ajax({
			  headers: vheaders,
			  type: meth,
			  processData: processDataflag,
			  url: url,
			  contentType: contType,
			  data: content
			}).done(function(msg,statusText,jqXhr) {
              $.unblockUI();
			  var end = new Date().getTime();
		      var time = end - start;
		      $('#restime').html(time+" ms");
			  var reshdrdata = "Status: " + jqXhr.status + "\n";
			  reshdrdata += jqXhr.getAllResponseHeaders();
			  $('#reshdrs').html(reshdrdata);
              var contentTypeHeader = jqXhr.getResponseHeader('Content-Type');
			  if(contentTypeHeader==null)
				 contentTypeHeader = jqXhr.getResponseHeader('Content-type');
			  if(contentTypeHeader==null)
				 contentTypeHeader = jqXhr.getResponseHeader('content-type');
			  if(contentTypeHeader==null)
			  contentTypeHeader = jqXhr.getResponseHeader('content-Type');
			  if(debugEnabled)alert("Completed Execute Test method call "+jqXhr.status+" "+statusText);
			  if(formName=="#Login" && typeof loginExtractionNm!="undefined" && typeof loginExtractionTyp!="undefined")
			  {
				  if(loginExtractionTyp=="json")
				  {
					  eval("var token = msg."+loginExtractionNm+";");
				  }
				  else if(loginExtractionTyp=="plain")
				  {
					  eval("var token = '"+jqXhr.responseText+"';");
				  }
				  else if(loginExtractionTyp=="header")
				  {
					  eval("var token = '"+jqXhr.getResponseHeader(loginExtractionNm)+"';");
				  }
				  else if(loginExtractionTyp=="cookie")
				  {
					  //eval("var token = '"+$.cookie(loginExtractionNm, {path: '/'})+"';");
				  }
				  else if(loginExtractionTyp=="xml")
				  {
					  if(loginExtractionNm.indexOf("//")==-1)
						loginExtractionNm = loginExtractionNm.replace(/./g, "//");	
					  if(loginExtractionNm.charAt(0)!='/')
						loginExtractionNm = "//"+loginExtractionNm;
					  
					  var nodeval = findXMLNodeValueByXpath(jqXhr.responseText, loginExtractionNm);
					  if(nodeval!=undefined && nodeval!="") {
						alert(nodeval);
						eval("var token = '"+nodeval+"';");
					  }
				  }
				  if(loginExtractionTyp=="header" || loginExtractionTyp=="plain")
				  {
					  $('#status').html(loginExtractionNm + " = " + token);
				  }
				  else 
				  {
					  printResponse(msg, jqXhr, contentTypeHeader);
				  }
				  if(typeof token!="undefined")
					 handleAuth(token);
			  }
			  else
			  {
				  printResponse(msg, jqXhr, contentTypeHeader);
			  }
			  if(!isUseBootstrapUI)
				  adjustHeight();
			  if(succFunc!=null)succFunc(jqXhr);
			}).fail(function(jqXhr, textStatus, msg) {
			  $.unblockUI();
			  var end = new Date().getTime();
		      var time = end - start;
		      $('#restime').html(time+" ms");
			  var reshdrdata = "Status: " + jqXhr.status + "\n";
			  reshdrdata += jqXhr.getAllResponseHeaders();
			  $('#reshdrs').html(reshdrdata);
			  var contentTypeHeader = jqXhr.getResponseHeader('Content-Type');
			  if(contentTypeHeader==null)
				 contentTypeHeader = jqXhr.getResponseHeader('Content-type');
			  if(contentTypeHeader==null)
				 contentTypeHeader = jqXhr.getResponseHeader('content-type');
			  if(contentTypeHeader==null)
				 contentTypeHeader = jqXhr.getResponseHeader('content-Type');
			  if(debugEnabled)alert("Completed Fail Execute Test method call "+jqXhr.status+" "+msg);
			  printResponse(msg, jqXhr, contentTypeHeader);
			  if(!isUseBootstrapUI)
				  adjustHeight();
			  if(failFunc!=null)failFunc(jqXhr);
			});
		}
	}
}

var oschars='abcdefghijklmnopqrstuvwxyz ~`!@#$%^&*()_+-=[]\\;\'/.,<>?:"{}|'; // allow whitespace
var ochars='abcdefghijklmnopqrstuvwxyz';
var nums='0123456789';  
function isInteger(value) { return value==parseInt(value); } // isInteger  
function isFloat(value)
{ 
	if(value=='')return true;
	return value==parseFloat(value);
}
function isStrictAlpha(value) 
{  
	if(value=='')return true;
	value=value.toLowerCase();  
	for(var i=0;i<value.length;i++)
	{
		if(ochars.indexOf(value.charAt(i))==-1) 
		{  
			value = '';
			return false; 
		}
	} 
	return true;  
}
function isAlpha(value) 
{  
	if(value=='')return true;
	value=value.toLowerCase();  
	for(var i=0;i<value.length;i++)
	{
		if(oschars.indexOf(value.charAt(i))==-1) 
		{  
			return false;  
		}
	}
	return true;  
}
function isNumber(value) 
{  
	if(value=='')return true;
	value=value.toLowerCase();  
	for(var i=0;i<value.length;i++)
	{
		if(nums.indexOf(value.charAt(i))==-1) 
		{  
			return false;  
		}
	}
	return true;  
}
function isAlphaNumeric(value) 
{  
	if(value=='')return true;
	value=value.toLowerCase();  
	for(var i=0;i<value.length;i++)
	{
		if(oschars.indexOf(value.charAt(i))==-1 && nums.indexOf(value.charAt(i))==-1) 
		{  
			return false;  
		}
	}
	return true;
}
function isBoolean(value)
{
	if(value=='')return true;
	if((typeof value=='string' && 
		(value.toLowerCase()=='true' || value.toLowerCase()=='yes' || value=='1'
		 || value.toLowerCase()=='false' || value.toLowerCase()=='no' || value=='0'))
		|| value==1 || value==0)
		return true;
	return false;
}
function validate(element, type)
{
	if(type=='boolean')
	{
		if(!isBoolean(element.value))
		{
			if(debugEnabled)alert('A Boolean value was expected..');
			element.value='';
		}
	}
	else if(type=='number' || type=='integer')
	{
		if(!isNumber(element.value))
		{
			if(debugEnabled)alert('A Numeric value was expected..');
			element.value='';
		}
	}
	else if(type=='aplha-strict')
	{
		if(!isStrictAlpha(element.value))
		{
			if(debugEnabled)alert('A Strict alphabetical value was expected..');
			element.value='';
		}
	}
	else if(type=='alpha')
	{
		if(!isAlpha(element.value))
		{
			if(debugEnabled)alert('A Alphabetical value was expected..');
			element.value='';
		}
	}
	else if(type=='alpha-numeric')
	{
		if(!isAlphaNumeric(element.value))
		{
			if(debugEnabled)alert('An Alphanumeric value was expected..');
			element.value='';
		}
	}
	else if(type=='float' || type=='double')
	{
		if(!isFloat(element.value))
		{
			if(debugEnabled)alert('A Float value was expected..');
			element.value='';
		}
	}
}

function toggle_visibility(id) {
   var e = document.getElementById(id);
   if(e.style.display == 'block')
	  e.style.display = 'none';
   else
	  e.style.display = 'block';
}

function getAuthenticationToken()
{
	var currUrl = document.location + "";
	var currToken = "";
	if(currUrl.indexOf("?")!=-1 && currUrl.indexOf(authTokNm+"=")!=-1)
	{
		currToken = currUrl.substr(currUrl.indexOf("?")+1);
		currToken = currToken.substr(currToken.indexOf("=")+1);
		if(currToken.indexOf("&")!=-1)
			currToken = currToken.substr(0, currToken.indexOf("&"));
	}
	return currToken;
}

function getAuthenticationToken(authTokName)
{
	var currUrl = document.location + "";
	var currToken = "";
	if(currUrl.indexOf("?")!=-1 && currUrl.indexOf(authTokName+"=")!=-1)
	{
		currToken = currUrl.substr(currUrl.indexOf("?")+1);
		currToken = currToken.substr(currToken.indexOf("=")+1);
		if(currToken.indexOf("&")!=-1)
			currToken = currToken.substr(0, currToken.indexOf("&"));
	}
	return currToken;
}

function blockPage()
{
	$.blockUI({ message: '<h3><img src="resources/busy.gif" /> Just a moment...</h3>' }); 
}

function unblockPage()
{
	$.unblockUI();
}

var countMap = {};
var schemaMap = {};

function addRemListElement(schemaNm, isAdd)
{
	var eleid = schemaNm;//.replace('.','').replace('[','').replace(']','');
	if(!isAdd && countMap[schemaNm]==0)
	{
		return;
	}
	else if(!isAdd)
	{
		countMap[schemaNm]--;
		var par = document.getElementById('_element_'+schemaNm);
		$(par.childNodes[par.childNodes.length-1]).remove();
		return;
	}
	
	var nschnm = schemaNm + "[" + (countMap[schemaNm]++) + "]";
	var isSpan = schemaMap[schemaNm].isSpan==undefined?false:schemaMap[schemaNm].isSpan;
	var isshowlabel = schemaMap[schemaNm].nolabel==undefined?true:!schemaMap[schemaNm].nolabel;
	var html = generateFromValue(schemaMap[schemaNm], nschnm, true, "", "orig_val_cls=\""+schemaNm+"\"", null, isSpan, isshowlabel, false, '');
	var div = document.createElement('div');
	div.innerHTML = html;
	div.style['border-bottom'] = "1px solid #C0C0C0";
	div.style['margin-bottom'] = "10px";
	div.style['padding-bottom'] = "10px";
	document.getElementById('_element_'+schemaNm).appendChild(div);
}

function addRemMapElement(schemaNm, isAdd)
{
	if(!isAdd && $('.map_key_cls').length==0 && countMap[schemaNm]==0)
	{
		return;
	}
	else if(!isAdd)
	{
		countMap[schemaNm]--;
		var par = document.getElementById('_element_'+schemaNm);
		$(par.childNodes[par.childNodes.length-1]).remove();
		//par.childNodes[par.childNodes.length-1].remove();
		return;
	}
	
	var contid = "_ele_"+schemaNm+(countMap[schemaNm]++);
	var html = generateFromValue(schemaMap[schemaNm].key, schemaNm, null, false, "map_key_cls", "onblur=\"updateMapValueNms(this)\" map_value_cls=\""+schemaNm+"\"", true, false, false, '');
	html += generateFromValue(schemaMap[schemaNm].value, schemaNm, null, true, schemaNm, "orig_val_cls=\""+schemaNm+"\"", true, false, false, '');
	var div = document.createElement('div');
	div.id = contid;
	div.innerHTML += html;
	document.getElementById('_element_'+schemaNm).appendChild(div);
	//document.getElementById('_element_'+schemaNm).innerHTML += "<div id=\""+contid+"\">" + html + "</div>";
	//$('#_element_'+schemaNm).append("<div id=\""+contid+"\">" + html + "</div>");
	$('.map_key_cls').each(function() {
		if(this.nodeName.toLowerCase()=="input")
			updateMapValueNms(this);
	});
}

function generateRandom(type)
{
	if(type=="string")
	{
		var text = "";
		var possible = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

		for( var i=0; i < 8; i++ )
			text += possible.charAt(Math.floor(Math.random() * possible.length));

		return text;
	}
	else if(type=="number" || type=="integer")
	{
		return Math.floor(Math.random() * (9999999 - 11111111 + 1)) + 11111111;
	}
	else if(type=="boolean")
	{
		return Math.random()<0.5;
	}
	else if(type=="date")
	{
		return new Date().toISOString();
	}
}

if (!document.getElementsByClassName) {
    document.getElementsByClassName=function(cn) {
        var allT=document.getElementsByTagName('*'), allCN=[], i=0, a;
        while(a=allT[i++]) {
            a.className==cn ? allCN[allCN.length]=a : null;
        }
        return allCN
    }
}

function getElementsByClassName(cn, doc) {
	var allT=doc.childNodes, allCN=[], i=0, a;
	while(a=allT[i++]) {
		if(!a.hasOwnProperty('className'))
			continue;
		var clsnms = a.className.split(" ");
		for(var j=0;j<clsnms.length;j++)
		{
			if(clsnms[j]==cn)
			{
				allCN[allCN.length]=a;
				break;
			}
		}
		if(a.childNodes!=undefined && a.childNodes.length>0)
		{
			var cllCN = getElementsByClassName(cn, a);
			if(cllCN.length>0)
			{
				for(var j=0;j<cllCN.length;j++)
				{
					allCN[allCN.length] = cllCN[j];
				}
			}
		}
	}
	return allCN
}

function plusminusmapfunc(event, node)
{
	if(node.nodeName.toLowerCase()=="a")
	{
		addRemMapElement(node.getAttribute('map_value_cls'), node.getAttribute('optype')=="true");
	}
	event.preventDefault();
	return false;
}

function plusminuslistfunc(event, node)
{
	if(node.nodeName.toLowerCase()=="a" || node.nodeName.toLowerCase()=="button")
	{
		addRemListElement(node.getAttribute('list_value_cls'), node.getAttribute('optype')=="true");
	}
	event.preventDefault();
	return false;
}