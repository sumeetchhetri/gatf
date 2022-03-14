var loadTestData = {
    hdr: null,
    dyg: null,
    points: []
};

isUseBootstrapUI = true;

var loadtestingdatatable = null;

if (typeof String.prototype.endsWith !== 'function') {
    String.prototype.endsWith = function(suffix) {
        return this.indexOf(suffix, this.length - suffix.length) !== -1;
    };
}

var loadtestingdatatablecnt = 0;

var schema = {
    "type": "object",
    "properties": {
        "baseUrl": {
            "type": "string"
        },
        "url": {
            "type": "string"
        },
        "name": {
            "type": "string"
        },
        "method": {
            "type": "string",
            "enum": ["GET", "POST", "PUT", "DELETE"]
        },
        "description": {
            "type": "string"
        },
        "content": {
            "type": "string",
            "ui": "textarea"
        },
        "headers": {
            "type": "map",
            "types": {
                "key": {
                    "type": "string",
                    "defaultIndex": 1,
                    "enum": ["", "Accept", "Accept-Charset", "Accept-Encoding",
                        "Accept-Language", "Allow", "Authorization", "Cache-Control",
                        "Content-Disposition", "Content-Encoding", "Content-ID",
                        "Content-Language", "Content-Length", "Content-Location",
                        "Content-Type", "Date", "ETag", "Expires", "Host", "If-Match",
                        "If-Modified-Since", "If-None-Match", "If-Unmodified-Since",
                        "Last-Modified", "Location", "Link", "Retry-After", "User-Agent",
                        "Vary", "WWW-Authenticate", "Cookie", "Set-Cookie"
                    ]
                },
                "value": {
                    "type": "string"
                }
            }
        },
        "exQueryPart": {
            "type": "string"
        },
        "expectedResCode": {
            "type": "integer",
            "defaultIndex": 0,
            "enum": [200, 201, 202, 204, 205, 206, 301, 302, 303, 304, 305, 307, 400, 401,
                402, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415, 416,
                417, 500, 501, 502, 503, 504, 505
            ]
        },
        "expectedResContentType": {
            "type": "string"
        },
        "expectedResContent": {
            "type": "string",
            "ui": "textarea"
        },
        "skipTest": {
            "type": "boolean",
            "required": true
        },
        "detailedLog": {
            "type": "boolean",
            "required": true
        },
        "secure": {
            "type": "boolean",
            "required": true
        },
        "expectedNodes": {
            "type": "array",
            "items": {
                "nolabel": true,
                "type": "string"
            }
        },
        "soapBase": {
            "type": "boolean",
            "required": true
        },
        "wsdlKey": {
            "type": "string"
        },
        "operationName": {
            "type": "string"
        },
        "soapParameterValues": {
            "type": "map",
            "types": {
                "key": {
                    "type": "string"
                },
                "value": {
                    "type": "string"
                }
            }
        },
        "workflowContextParameterMap": {
            "type": "map",
            "types": {
                "key": {
                    "type": "string"
                },
                "value": {
                    "type": "string"
                }
            }
        },
        "sequence": {
            "type": "integer"
        },
        "filesToUpload": {
            "type": "array",
            "items": {
                "nolabel": true,
                "type": "string"
            }
        },
        "outFileName": {
            "type": "string"
        },
        "repeatScenarioProviderName": {
            "type": "string",
            "defaultIndex": 0,
            "enumVar": "miscMap['providers']"
        },
        "numberOfExecutions": {
            "type": "integer"
        },
        "repeatScenariosConcurrentExecution": {
            "type": "boolean",
            "required": true
        },
        "stopOnFirstFailureForPerfTest": {
            "type": "boolean",
            "required": true
        },
        "simulationNumber": {
            "type": "integer"
        },
        "preWaitMs": {
            "type": "number"
        },
        "postWaitMs": {
            "type": "number"
        },
        "reportResponseContent": {
            "type": "boolean",
            "defaultIndex": 1
        },
        "preExecutionDataSourceHookName": {
            "type": "string",
            "defaultIndex": 0,
            "enumVar": "miscMap['hooks']"
        },
        "postExecutionDataSourceHookName": {
            "type": "string",
            "defaultIndex": 0,
            "enumVar": "miscMap['hooks']"
        },
        "abortOnInvalidStatusCode": {
            "type": "boolean",
            "required": true
        },
        "abortOnInvalidContentType": {
            "type": "boolean",
            "required": true
        },
        "relatedTestName": {
            "type": "string",
            "defaultIndex": 0,
            "enumVar": "currtestcases"
        },
        "executeOnCondition": {
            "type": "string"
        },
        "logicalValidations": {
            "type": "array",
            "items": {
                "nolabel": true,
                "type": "string"
            }
        },
        "perfConfig": {
            "label": {
                "type": "section",
                "value": "Performance Test Details"
            },
            "type": "object",
            "properties": {
                "type": {
                    "type": "string",
                    "enum": ["none", "wrk", "wrk2", "vegeta", "autocannon"]
                },
                "connections": {
                    "type": "number"
                },
                "durationSeconds": {
                    "type": "number"
                },
                "threads": {
                    "type": "number"
                },
                "rate": {
                    "type": "number"
                },
                "filePath": {
                    "type": "string"
                },
                "timeout": {
                    "type": "number"
                },
                "latency": {
                    "type": "boolean"
                },
                "extras": {
                    "type": "string"
                }
            }
        }
    }
};

function saveAsPngFile() {
    html2canvas(document.querySelector('#ExampleBeanServiceImpl_form')).then(function(canvas) {
        var tim = "TestDetails_" + new Date().getTime() + ".png";
        saveAs(Canvas2Image.convertToPNG(canvas, canvas.width, canvas.height).src, tim);
    });
}

function isPrimitive(type) {
    return (type == 'string' || type == 'number' || type == 'integer' ||
        type == 'boolean' || type == 'date' || type == 'float' ||
        type == 'double');
}

function showInpTitle(ele) {
    ele.title = ele.value;
}

function decorateList(schema, list, acValue, attrs, nmdef, addclses, label, divstyle, width) {
    var defval = schema.hasOwnProperty('default') ? schema['default'] : null;
    var defind = schema.hasOwnProperty('defaultIndex') ? schema['defaultIndex'] : null;

    //Check the defaultIndex first, if valid use it
    defind = (list != null && defind < list.length && defind >= 0) ? defind : null;
    if (defind == null && defval != null && acValue == null)
        acValue = defval;

    if (acValue != null) {
        if (list != null) {
            defind = null;
            for (var i = 0; i < list.length; i++) {
                if (list[i] == (acValue + "")) {
                    defind = i;
                    break;
                }
            }
        }
        if (defind == null && acValue != '') {
            return ("<div " + divstyle + " class=\"form-elems controls\">" + label + "<input mo-event=\"showInpTitle(this)\" " + attrs + " " + nmdef + " class=\"form-control " + addclses + "\" blur-event=\"validate(this, '" + schema.type + "')\" value=\"" + acValue + "\" " + width + " type='text'/></div>");
        }
    }
    var html = "<div " + divstyle + " class=\"form-elems controls\">" + label + "<div><select change-event='handleBlankSelect(this)' " + attrs + " " + nmdef + " class=\"form-control " + addclses + "\" " + width + ">";
    if (list != null) {
        for (var i = 0; i < list.length; i++) {
            var issel = (defind != null && defind == i) ? "selected" : "";
            html += "<option value=\"" + list[i] + "\" " + issel + ">" + list[i] + "</option>";
        }
    }
    return html + ("</select><input " + attrs + " class=\"form-control " + addclses + "\" blur-event=\"validate(this, '" + schema.type + "')\" " + width + " type='hidden'/></div></div>");
}

function handleBlankSelect(elem) {
    if (elem.value == "") {
        var parentN = elem.parentNode;
        var name = elem.name;
        elem.setAttribute("name", "");
        $(elem).hide();
        var inp = parentN.childNodes[parentN.childNodes.length - 1];
        inp.setAttribute("name", name);
        inp.type = "text";
    }
}

function generateFromValue(schema, heirar, isnm, addclas, labinpdet, respValue, isObjspan, isshowlabel, isTop, propLabel) {
    if (isPrimitive(schema.type)) {
        var valut = respValue == null ? '' : respValue;
        var ovalut = respValue;
        if (typeof valut == 'string' && valut != '') {
            valut = valut.replace(/"/g, "&quot;");
        }
        var nmdef = (isnm ? ("name=\"" + heirar + "\"") : "");
        var dtcls = "";
        if (schema.type == 'date')
            dtcls = "clsDatePicker";
        var isAttr = "";
        var label = isshowlabel ? "<label class=\"" + addclas + "\" " + labinpdet + ">" + propLabel + "</label>" : "";
        var divstyle = isObjspan ? " style=\"display:inline-block;margin-left:10px\" " : "";
        var width = isObjspan ? "" : "style=\"width: 70%; height: 30px;\"";
        if (schema.hasOwnProperty('isattr')) {
            isAttr = "isattr=\"true\";"
        }
        if (schema.type == ('boolean')) {
            return decorateList(schema, ["false", "true"], ovalut, isAttr + " " + labinpdet, nmdef, addclas + "" + dtcls, label, divstyle, width);
        } else if (schema.hasOwnProperty('enum') && schema.enum.length > 0) {
            return decorateList(schema, schema.enum, ovalut, isAttr + " " + labinpdet, nmdef, addclas + "" + dtcls, label, divstyle, width);
        } else if (schema.hasOwnProperty('enumVar')) {
            return decorateList(schema, schema.enumVar, ovalut, isAttr + " " + labinpdet, nmdef, addclas + "" + dtcls, label, divstyle, width);
        } else {
            var sp1 = isAttr + " " + labinpdet;
            var sp2 = addclas + "" + dtcls;
            if (schema.hasOwnProperty('ui') && schema.ui == 'textarea') {
                return ("<div " + divstyle + " class=\"form-elems controls\">" + label + "<textarea mo-event=\"showInpTitle(this)\" " + sp1 + " " + nmdef + " class=\"form-control " + sp2 + "\" blur-event=\"validate(this, '" + schema.type + "')\" style=\"width:70%;height:200px;\">" + valut + "</textarea></div>");

            } else {
                return ("<div " + divstyle + " class=\"form-elems controls\">" + label + "<input mo-event=\"showInpTitle(this)\" " + sp1 + " " + nmdef + " class=\"form-control " + sp2 + "\" blur-event=\"validate(this, '" + schema.type + "')\" value=\"" + valut + "\" " + width + " type='text'/></div>");
            }
        }
    } else if (schema.type == 'object') {
        if (schema.hasOwnProperty('properties')) {
            if (!heirar && schema.hasOwnProperty('name')) {
                if (!heirar)
                    heirar = schema.name;
                else
                    heirar += "['" + schema.name + "']";
            }
            var vald = respValue == null ? {} : respValue;
            var slab = '';
            if (schema.hasOwnProperty('label') && schema.label != undefined) {
                slab = '<h4>' + schema.label.value + '</h4><br/>';
            }
            //var sty = isTop?"":"style=\"border:1px dotted\"";
            var html = "<div class=\"control-group\">" + slab;
            for (var property in schema.properties) {
                if (schema.properties.hasOwnProperty(property)) {
                    var hirNm = heirar;
                    if (!hirNm || hirNm == "")
                        hirNm = property;
                    else
                        hirNm += "['" + property + "']";

                    isObjspan = isObjspan && schema.hasOwnProperty("isSpan") && schema.isSpan;
                    if (schema.hasOwnProperty("nolabel") && schema.nolabel)
                        isshowlabel = false;

                    var propobj = schema.properties[property];
                    var htmlp = generateFromValue(propobj, hirNm, isnm, addclas, "orig_val_cls=\"" + hirNm + "\"", vald[property], isObjspan, isshowlabel, false, property);
                    html += htmlp;
                }
            }
            return html + "</div>";
        }
    } else if (schema.type == 'array') {
        if (schema.hasOwnProperty('items')) {
            //schema.items.nolabel = true;
            var hirNm = heirar;
            if (!hirNm || hirNm == "")
                hirNm = "";
            if (!countMap.hasOwnProperty(hirNm))
                countMap[hirNm] = 0;
            schemaMap[hirNm] = schema.items;
            var vald = respValue == null ? [] : respValue;
            var rvalue = null;
            if (vald.length > countMap[hirNm])
                rvalue = vald[countMap[hirNm]];
            var slab = '';
            if (schema.hasOwnProperty('label') && schema.label != undefined) {
                slab = '<h4>' + schema.label.value + '</h4><br/>';
            }
            var html = '<div id="_element_' + hirNm + '" class="form-elems" style="border:1px dotted black;padding:10px;margin-top:10px;">' + slab + '<b>' + propLabel + '</b>&nbsp;&nbsp;<button class="plusminuslist" optype="true" list_value_cls="' + hirNm + '" click-event=\"plusminuslistfunc(event, this)\">Add</button>&nbsp;&nbsp;<button list_value_cls="' + hirNm + '" class="plusminuslist" optype="false" click-event=\"plusminuslistfunc(event, this)\">Remove</button><br/><br/>';
            var isshowlabel = schema.items.nolabel == undefined ? true : !schema.items.nolabel;

            if (vald.length > 0) {
                for (var rvi = 0; rvi < vald.length; rvi++) {
                    var nhirNm = hirNm + "[" + rvi + "]";
                    countMap[hirNm]++;
                    html += "<div style=\"border-bottom:1px solid #C0C0C0;margin-bottom:10px;padding-bottom:10px\">" + generateFromValue(schema.items, nhirNm, isnm, addclas, "orig_val_cls=\"" + nhirNm + "\"", vald[rvi], false, isshowlabel, false, hhirNm) + "</div>";
                }
            }
            html += "</div>";
            return html;
        }
    } else if (schema.type == 'marray') {
        if (schema.hasOwnProperty('items')) {
            var hirNm = heirar;
            if (!hirNm || hirNm == "")
                hirNm = "";
            if (!countMap.hasOwnProperty(hirNm))
                countMap[hirNm] = 0;
            schemaMap[hirNm] = schema.items;
            var vald = respValue == null ? {} : respValue;
            var rvalue = null;
            //if(vald.length>countMap[hirNm])
            //	rvalue = vald[countMap[hirNm]];
            var slab = '';
            if (schema.hasOwnProperty('label') && schema.label != undefined) {
                slab = '<h4>' + schema.label.value + '</h4><br/>';
            }
            var html = '<div id="_element_' + hirNm + '" class="form-elems" style="border:1px dotted black;padding:10px;margin-top:10px;">' + slab + '<b>' + propLabel + '</b>&nbsp;&nbsp;<button class="plusminuslist" optype="true" list_value_cls="' + hirNm + '" click-event=\"plusminuslistfunc(event, this)\">Add</button>&nbsp;&nbsp;<button list_value_cls="' + hirNm + '" class="plusminuslist" optype="false" click-event=\"plusminuslistfunc(event, this)\">Remove</button><br/><br/>';

            var isSpan = schema.items.isSpan == undefined ? false : schema.items.isSpan;
            var isshowlabel = schema.items.nolabel == undefined ? true : !schema.items.nolabel;

            var propobj = schema.items.properties.value;
            var keyobj = schema.items.properties.key;
            for (var property in vald) {
                if (vald.hasOwnProperty(property)) {
                    var hhirNm = hirNm + "[" + (countMap[hirNm]) + "]" + "['key']";
                    html += "<div style=\"border-bottom:1px solid #C0C0C0;margin-bottom:10px;padding-bottom:10px\">" + generateFromValue(keyobj, hhirNm, isnm, addclas, "orig_val_cls=\"" + hhirNm + "\"", property, isSpan, isshowlabel, false, property);
                    hhirNm = hirNm + "[" + (countMap[hirNm]) + "]" + "['value']";
                    html += generateFromValue(propobj, hhirNm, isnm, addclas, "orig_val_cls=\"" + hhirNm + "\"", vald[property], isSpan, isshowlabel, false, hhirNm) + "</div>";
                    countMap[hirNm]++;
                }
            }
            html += "</div>";
            return html;
        }
    } else if (schema.type == 'map') {
        var hirNm = heirar;
        if (!hirNm)
            hirNm = "";
        if (schema.hasOwnProperty('types')) {
            if (schema.types.hasOwnProperty('key') && schema.types.hasOwnProperty('value')) {
                var updschema = {};
                updschema.type = "marray";
                updschema.mtype = "map";
                updschema.items = {};
                updschema.items.isSpan = true;
                updschema.items.nolabel = true;
                updschema.items.type = "object";
                updschema.items.properties = {};
                updschema.items.properties.key = schema.types.key;
                updschema.items.properties.value = schema.types.value;
                updschema.label = schema.label;
                schemaMap[hirNm + "_dummymap"] = updschema;
                return generateFromValue(updschema, hirNm, isnm, addclas, labinpdet, respValue, false, true, false, propLabel);
            }
        }
    }
}

function updateMapValueNms(key) {
    var clsnm = key.getAttribute('map_value_cls');
    if (key.value.trim() == "") return;
    if (!key.hasOwnProperty('lastclsnm'))
        key.lastclsnm = "";
    var nclsnm = clsnm;
    var kval = key.value.replace(/\'/g, "\'");
    if (nclsnm.lastIndexOf("[") != -1)
        nclsnm = nclsnm.substring(0, nclsnm.lastIndexOf("[")) + "['" + kval + "']" + nclsnm.substring(nclsnm.lastIndexOf("["));
    else
        nclsnm += "['" + kval + "']";
    var nodes = getElementsByClassName(clsnm, key.parentNode.parentNode);
    for (var i = 0; i < nodes.length; i++) {
        var node = nodes[i];
        node.className = node.className.replace(key.lastclsnm, "");
        node.className += " " + nclsnm + " ";
        if (node.nodeName.toLowerCase() == "input") {
            var nnnm = node.getAttribute('orig_val_cls');
            nnnm += "['" + kval + "']";
            node.name = nnnm
        } else if (node.nodeName.toLowerCase() == "label") {
            var nnnm = node.getAttribute('orig_val_cls');
            nnnm += "['" + kval + "']";
            node.name = nnnm
            node.innerHTML = nnnm
        }
    }
    key.lastclsnm = " " + nclsnm + " ";
}

function loadURL() {
    var urlValue = readCookie('ExampleBeanServiceImpl_form');
    if (urlValue != null) {
        $('#93be7b20299b11e281c10800200c9a66_URL').val(urlValue);
    }
}


function updatetawidcont() {
    var cont = "";
    cont = JSON.stringify($('#ExampleBeanServiceImpl_form').serializeObject(), undefined, "\t");
    if (cont != "" && $('#req-txtarea').length > 0) {
        $('#req-txtarea').val(cont).trigger('autosize.resize');
    }
}

var firstFile = '';
var currtestcasefile;
var miscMap = {};
var currtestcases = [''];
var alltestcasefiles = [];

(function() {
    debugEnabled = false;
    serverUrl = "http://localhost:9080/";

    $(document).ready(function() {
        ajaxCall(true, "GET", "misc", "", "", {}, function(data) {
            miscMap = data;
            startInitConfigTool(configuration);
        }, null);

		$('#srch-term').on('keydown', searchLeftNavs);

		$(document).off('click').on('click', function(e) {
		    if(e.target && (e.target.tagName=='A' || e.target.tagName=='BUTTON') && $(e.target).attr('click-event')) {
		        var evt = $(e.target).attr('click-event');
				execFunction(evt, $(e.target));
		    }
		});
    });
})();

function execFunction(evt, ths) {
	ths = ths[0];
	evt = evt.trim();
	var fnm = evt.substring(0, evt.indexOf("("));
	var argss = evt.substring(evt.indexOf("(")+1, evt.length-1);
	argss = argss.split(',');
	var args = [];
	for(var i=0;i<argss.length;i++) {
		var tmp = argss[i].trim();
		if(tmp) {
			if(tmp.charAt(0)=="'" && tmp.charAt(tmp.length-1)=="'") {
				tmp = tmp.substring(1, tmp.length-1);
				args.push(tmp);
			} else if(tmp.charAt(0)=='"' && tmp.charAt(tmp.length-1)=='"') {
				tmp = tmp.substring(1, tmp.length-1);
				args.push(tmp);
			} else if(tmp=="this" || tmp.startsWith("this")) {
				if(tmp=="this") {
					args.push(ths);
				} else {
					var tmp1 = tmp.substring(5);
					if(tmp1=="value") {
						args.push(ths.value);
					} else if(tmp1=="html") {
						args.push(ths.html);
					} else if(tmp1=="text") {
						args.push(ths.text);
					}
				}
			} else if(tmp=="event") {
				args.push($(event)[0]);
			} else if(tmp=='true' || tmp=='false') {
				args.push(tmp=='true');
			} else if(!isNaN(tmp)) {
				args.push(tmp*1);
			} else if(tmp=="null") {
		       	args.push(null);
		    } else if(tmp=="undefined") {
		       	args.push(undefined);
		    } else if(window[tmp]) {
		       	args.push(window[tmp]);
		    } else {
				args.push(eval(tmp));
			}
		}
	}
	if(window[fnm]) {
		window[fnm].apply(null, args);
	}
}

function getProfiles() {
    $('#heading_main').html('Data Source Statistics');
    $('#ExampleBeanServiceImpl_form').html('');
    ajaxCall(true, "GET", "profile?dsnames=all", "", "", {}, function(data1) {
        for (var property in data1) {
            if (data1.hasOwnProperty(property)) {
                var htm = '<u><b>' + property + '</u></b><br/><br/><table id="' + property + '" class="table table-striped table-bordered table-hover" width="100%" style="width:100%;table-layout:fixed;word-wrap:break-word;color:black">';
                var ttable;
                for (var ind = 0; ind < data1[property].length; ind++) {
                    if (ind == 0) {
                        htm += '<thead><tr>';
                        for (var ind1 = 0; ind1 < data1[property][ind].length; ind1++) {
                            htm += '<th style="color:black">' + data1[property][ind][ind1] + '</th>';
                        }
                        htm += '</tr></thead></table><p>&nbsp;</p><p>&nbsp;</p><p>&nbsp;</p><p>&nbsp;</p><p>&nbsp;</p><p>&nbsp;</p>';
                        $('#ExampleBeanServiceImpl_form').append(htm);
                        ttable = $('#' + property).dataTable({
                            "bPaginate": true,
                            "bFilter": true,
                            "bJQueryUI": true,
                            "bSort": true,
                            "bDestroy": true,
                            "iDisplayLength": 10,
                            "dom": 'Tlfrtip',
                            "buttons": [
                                'copy', 'csv', 'excel', 'pdf', 'print'
                            ]
                        });
                    }
                    ttable.fnAddData(data1[property][ind], true);
                }

            }
        }
    }, null);
    return false;
}

function executeHtml(pluginType) {
    var htmm = '<a href="#" class="plusminuslist" click-event=\"executionHandler(\'PUT\', true, \'' + pluginType + '\')\">Start Execution</a><br/></br/><a href="#" class="plusminuslist" click-event=\"executionHandler(\'DELETE\', true, \'' + pluginType + '\')\">Stop Execution</a><br/></br/><a href="#" class="plusminuslist" click-event=\"executionHandler(\'GET\', true, \'' + pluginType + '\')\">Check Execution Status</a><br/><br/><image id="image_status" src="resources/yellow.png"/>';

    if (pluginType == 'executor') {
        if (!calledbytestfpage) {
            execFiles = new Array();
        }
        calledbytestfpage = false;
        //if(!isSeleniumExecutor) {
        htmm += '<br/><br/><p>Overall Statistics</p><table id="lol_tbl"><tr><th>Tot Runs</th><th>Tot Tests</th><th>Failed Tests</th><th>Skip. Tests</th><th>Execution Time</th><th>Total Time</th></tr><tr><td id="lol_tr"></td><td id="lol_tt"></td><td id="lol_ft"></td><td id="lol_st"></td><td id="lol_eti"></td><td id="lol_tti"></td></tr></table><br><table id="lol_hdr"><thead><tr><th>Max</th><th>Min</th><th>Std Dev</th><th>Mean</th><th>50%</th><th>75%</th></tr></thead><tbody id="hdr1"></tbody><thead><tr><th>90%</th><th>97.5%</th><th>99%</th><th>99.9%</th><th>99.99%</th><th>99.999%</th></tr></thead><tbody id="hdr2"></tbody></table><div class="hidden"><br/>Subtest Statistics<br/><table class="table table-striped table-bordered table-hover" id="lol_sts" width="100%" style="width:100%;table-layout:fixed;word-wrap:break-word;color:black"><thead><tr><th style="color:black">Run No.&nbsp;&nbsp;</th><th style="color:black">Tot Tests</th><th style="color:black">Success Tests</th><th style="color:black">Fail. Tests</th></tr></thead><tbody></tbody></table></div><br/><p>Run-Wise Statistics</p></table><table class="table table-striped table-bordered table-hover" id="lol_tblcu" width="100%" style="width:100%;table-layout:fixed;word-wrap:break-word;color:black"><thead><tr><th style="color:black">Run No.&nbsp;&nbsp;</th><th style="color:black">Tot Tests</th><th style="color:black">Failed Tests</th><th style="color:black">Skip. Tests</th><th style="color:black">Time</th></tr></thead><tbody></tbody></table>';
        //}
        $('#ExampleBeanServiceImpl_form').html(htmm);
        if (isSeleniumExecutor) {
            $('#lol_sts').parent().removeClass('hidden');
        }
        /*loadtestingdatatable = $('#lol_tblcu').dataTable({
			"aoColumns": [
				{"sWidth" : "20%", "sType": "alphanum"},
				{"sWidth" : "20%"},
				{"sWidth" : "20%"},
				{"sWidth" : "20%"},
				{"sWidth" : "20%"}
			 ],
			"aaSorting" : [],
			"bPaginate": false,
			"bFilter": false,
			"bJQueryUI": true,
			"bSort": true,
			"bDestroy": true,
			"iDisplayLength": 100,
			"dom": 'Tlfrtip',
	        "buttons": [
	            'copy', 'csv', 'excel', 'pdf', 'print'
	        ]
		});*/
        loadtestingdatatablecnt = 0;
        $('#heading_main').html('Execute Tests');
    } else {
        $('#heading_main').html('Generate Tests');
        $('#ExampleBeanServiceImpl_form').html(htmm);
    }
}

function getExtIntData(extApiFileName, configType) {
    var isExternal = configType == 'issuetrackerapi' ? true : false;
    ajaxCall(true, "GET", "testcases?testcaseFileName=" + extApiFileName + "&configType=" + configType, "", "", {}, function(isExternal, extApiFileName, configType) {
        return function(data1) {
            currtestcasefile = extApiFileName;
            var htmm = '';
            if (data1 != null && data1.length > 0) {
                htmm += '<table border="1">';
                for (var t1 = 0; t1 < data1.length; t1++) {
                    var tcid = 'tc_' + t1;
                    htmm += '<tr><td><b class="' + data1[t1]["method"].toLowerCase() + 'big">' + data1[t1]["method"] + '</b></td><td><a href="#" id="' + tcid + '">' + data1[t1]["name"] + '</a></td></tr>';
                }
                htmm += '</table>';
                $('#ExampleBeanServiceImpl_form').html(htmm);
                for (var t1 = 0; t1 < data1.length; t1++) {
                    var tcid = 'tc_' + t1;
                    $('#' + tcid).attr('tcfname', data1[t1]["name"]);
                    document.getElementById(tcid).data = data1[t1];
                    $('#' + tcid).click(function(configType, extApiFileName, isExternal) {
                        return function() {
                            addTestCase(false, this.data, configType, extApiFileName, !isExternal, isExternal);
                        };
                    }(configType, extApiFileName, isExternal));
                }
            } else {
                var htmm = '<table border="1">';
                htmm += '<tr><td><b class="postbig">POST</b></td><td><a href="#" click-event="extaddTestCase(true, \'authapi\', \'' + configType + '\')">authapi</a></td></tr>';
                htmm += '<tr><td><b class="postbig">POST</b></td><td><a href="#" click-event="extaddTestCase(true, \'targetapi\', \'' + configType + '\')">targetapi</a></td></tr>';
                htmm += '</table>';
                $('#ExampleBeanServiceImpl_form').html(htmm);
            }
        };
    }(isExternal, extApiFileName, configType), null);
}

function extaddTestCase(isNew, type, configType) {
    var isExternal = configType == 'issuetrackerapi' ? true : false;
    addTestCase(true, {
        name: type
    }, configType, 'gatf-logging-api-int.xml', !isExternal, isExternal);
}

$.fn.dataTable.ext.type.order["alphanum-desc"] = function(a, b) {
    if (a.indexOf('</b>') != -1)
        a = a.substring(0, a.indexOf('</b>'));
    if (b.indexOf('</b>') != -1)
        b = b.substring(0, b.indexOf('</b>'));
    if (a.indexOf('#') != -1)
        a = a.substring(a.indexOf('#') + 1);
    if (b.indexOf('#') != -1)
        b = b.substring(b.indexOf('#') + 1);
    var aa = a.replace(/[^0-9]+/g, '').replace('.', ''),
        bb = b.replace(/[^0-9]+/g, '').replace('.', '');
    if (aa != '' && bb != '') {
        aa = parseInt(aa);
        bb = parseInt(bb);
        return aa == bb ? 0 : (aa < bb ? 1 : -1);
    } else {
        aa = a;
        bb = b;
        return 0;
    }
};

$.fn.dataTable.ext.type.order["alphanum-asc"] = function(a, b) {
    if (a.indexOf('</b>') != -1)
        a = a.substring(0, a.indexOf('</b>'));
    if (b.indexOf('</b>') != -1)
        b = b.substring(0, b.indexOf('</b>'));
    if (a.indexOf('#') != -1)
        a = a.substring(a.indexOf('#') + 1);
    if (b.indexOf('#') != -1)
        b = b.substring(b.indexOf('#') + 1);
    var aa = a.replace(/[^0-9]+/g, '').replace('.', ''),
        bb = b.replace(/[^0-9]+/g, '').replace('.', '');
    if (aa != '' && bb != '') {
        aa = parseInt(aa);
        bb = parseInt(bb);
        return aa == bb ? 0 : (aa < bb ? -1 : 1);
    } else {
        aa = a;
        bb = b;
        return 0;
    }
};

//var execTimes = {};
var simplifyInterval;
var execFiles = new Array();

function executionHandler(method, shwPp, pluginType) {
    var cdt = '';
    var cdttype = '';
    if (method == 'PUT' && pluginType == 'executor') {
        cdt = JSON.stringify(execFiles, undefined, 4);
        cdttype = 'application/json';
    }
    ajaxCall(false, method, "execute?pluginType=" + pluginType, cdttype, cdt, {}, function(shwPp, pluginType) {
        return function(data) {
            if (shwPp) alert(data);
            if (data.error == 'Execution already in progress..' || data.error == "Execution completed, check Reports Section") {
                if (data.error == "Execution completed, check Reports Section") {
                    $("#image_status").attr("src", "resources/green.png");
                    if (loadTestData.hdr) {
                        $('#hdr1').html('<tr><td>' + loadTestData.hdr.maxValue + '</td><td>' + loadTestData.hdr.minNonZeroValue + '</td><td>' + loadTestData.hdr.stdDeviation.toFixed(2) + '</td><td>' + loadTestData.hdr.mean.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p50.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p75.toFixed(2) + '</td></tr/>');
                        $('#hdr2').html('<tr><td>' + loadTestData.hdr.summary.p90.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p97_5.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p99.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p99_9.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p99_99.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p99_999.toFixed(2) + '</td></tr/>');
                    }
                    $('#image_status').off().on('click', function() {
                        saveAsPngFile();
                    });
                } else {
                    setTimeout(function(pluginType) {
                        return function() {
                            executionHandler('GET', false, pluginType)
                        };
                    }(pluginType), 1000);
                }
                if (data["lstats"] && data["lstats"].length > 0) {
                    loadStatisticsInTable(data["lstats"]);
                }
                if (data["sstats"] && data["sstats"].length > 0) {
                    loadStatisticsInTable2(data["sstats"]);
                }
                if (data["gstats"]) {
                    $('#lol_tr').html(data["gstats"].totalSuiteRuns);
                    $('#lol_tt').html(data["gstats"].totalTestCount);
                    $('#lol_ft').html(data["gstats"].failedTestCount);
                    $('#lol_st').html(data["gstats"].skippedTestCount);
                    $('#lol_tti').html(data["gstats"].executionTime);
                    $('#lol_eti').html(data["gstats"].actualExecutionTime);
                }
            } else if (data.error.indexOf('Execution failed with Error - ') != -1) {
                $("#image_status").attr("src", "resources/red.png");
            } else if (data.error == "Please Start the Execution....") {
                $("#image_status").attr("src", "resources/yellow.png");
            } else if (data.error == "Testcase execution is not in progress...") {
                $("#image_status").attr("src", "resources/yellow.png");
            } else if (data.error == "Unknown Error...") {
                $("#image_status").attr("src", "resources/red.png");
            } else if (data.error == "Execution Started") {
                if (data.loadTestingEnabled === true) {
                    if (loadTestData.hdr) {
                        loadTestData.hdr.destroy();
                    }
                    loadTestData.hdr = hdr.build({
                        bitBucketSize: 32, // may be 8, 16, 32, 64 or 'packed'
                        autoResize: true, // default value is true
                        lowestDiscernibleValue: 1, // default value is also 1
                        highestTrackableValue: Number.MAX_SAFE_INTEGER, // can increase up to Number.MAX_SAFE_INTEGER
                        numberOfSignificantValueDigits: 3, // Number between 1 and 5 (inclusive)
                        useWebAssembly: false // default value is false, see WebAssembly section for details
                    });
                    loadTestData.points = [];
                    //$('#lol_tblcu').parent().append('<div class="canvas-container cf"> <p class="tolerance-container"><input id="tolerance" type="text" value="0.8" /></p> <canvas id="canvas" width="800" height="400"></canvas> </div>');
                    $('<br/><div id="div_g" style="width:97%; height:400px;"></div><br/>').insertBefore('#lol_tblcu');
                    //loadsimplify(true);
                    //simplifyInterval = setInterval(loadsimplify, 2000);
                    if (loadTestData.dyg) {
                        loadTestData.dyg.destroy();
                    }
                    loadTestData.dyg = new Dygraph(document.getElementById("div_g"), loadTestData.points, {
                        drawPoints: true,
                        showRoller: true,
                        labels: ['Time', 'ExecutionTime']
                    });
                }
                currltsnum = 0;
                $("#image_status").attr("src", "resources/red_anime.gif");
                setTimeout(function(pluginType) {
                    return function() {
                        executionHandler('GET', false, pluginType)
                    };
                }(pluginType), 1000);
            }
        };
    }(shwPp, pluginType), function(shwPp, pluginType) {
        return function(data) {
            if (shwPp) alert(data.error);
            if (data.error == 'Execution already in progress..' || data.error == "Execution completed, check Reports Section") {
                if (data.error == "Execution completed, check Reports Section") {
                    $("#image_status").attr("src", "resources/green.png");
                    if (loadTestData.hdr) {
                        $('#hdr1').html('<tr><td>' + loadTestData.hdr.maxValue + '</td><td>' + loadTestData.hdr.minNonZeroValue + '</td><td>' + loadTestData.hdr.stdDeviation.toFixed(2) + '</td><td>' + loadTestData.hdr.mean.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p50.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p75.toFixed(2) + '</td></tr/>');
                        $('#hdr2').html('<tr><td>' + loadTestData.hdr.summary.p90.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p97_5.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p99.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p99_9.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p99_99.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p99_999.toFixed(2) + '</td></tr/>');
                    }
                    $('#image_status').off().on('click', function() {
                        saveAsPngFile();
                    });
                } else {
                    setTimeout(function(pluginType) {
                        return function() {
                            executionHandler('GET', false, pluginType)
                        };
                    }(pluginType), 1000);
                }
                if (data["lstats"] && data["lstats"].length > 0) {
                    loadStatisticsInTable(data["lstats"]);
                }
                if (data["sstats"] && data["sstats"].length > 0) {
                    loadStatisticsInTable2(data["sstats"]);
                }
                if (data["gstats"]) {
                    $('#lol_tr').html(data["gstats"].totalSuiteRuns);
                    $('#lol_tt').html(data["gstats"].totalTestCount);
                    $('#lol_ft').html(data["gstats"].failedTestCount);
                    $('#lol_st').html(data["gstats"].skippedTestCount);
                    $('#lol_tti').html(data["gstats"].executionTime);
                    $('#lol_eti').html(data["gstats"].actualExecutionTime);
                }
            } else if (data.error.indexOf('Execution failed with Error - ') != -1) {
                $("#image_status").attr("src", "resources/red.png");
            } else if (data.error == "Please Start the Execution....") {
                $("#image_status").attr("src", "resources/yellow.png");
            } else if (data.error == "Testcase execution is not in progress...") {
                $("#image_status").attr("src", "resources/yellow.png");
            } else if (data.error == "Unknown Error...") {
                $("#image_status").attr("src", "resources/red.png");
            } else if (data.error == "Execution Started") {
                if (data.loadTestingEnabled === true) {
                    if (loadTestData.hdr) {
                        loadTestData.hdr.destroy();
                    }
                    loadTestData.hdr = hdr.build({
                        bitBucketSize: 32, // may be 8, 16, 32, 64 or 'packed'
                        autoResize: true, // default value is true
                        lowestDiscernibleValue: 1, // default value is also 1
                        highestTrackableValue: Number.MAX_SAFE_INTEGER, // can increase up to Number.MAX_SAFE_INTEGER
                        numberOfSignificantValueDigits: 3, // Number between 1 and 5 (inclusive)
                        useWebAssembly: false // default value is false, see WebAssembly section for details
                    });
                    loadTestData.points = [];
                    //$('#lol_tblcu').parent().append('<div class="canvas-container cf"> <p class="tolerance-container"><input id="tolerance" type="text" value="0.8" /></p> <canvas id="canvas" width="800" height="400"></canvas> </div>');
                    $('<br/><div id="div_g" style="width:97%; height:400px;"></div><br/>').insertBefore('#lol_tblcu');
                    //loadsimplify(true);
                    //simplifyInterval = setInterval(loadsimplify, 2000);
                    if (loadTestData.dyg) {
                        loadTestData.dyg.destroy();
                    }
                    loadTestData.dyg = new Dygraph(document.getElementById("div_g"), loadTestData.points, {
                        drawPoints: true,
                        showRoller: true,
                        labels: ['Time', 'ExecutionTime']
                    });
                }
                currltsnum = 0;
                $("#image_status").attr("src", "resources/red_anime.gif");
                setTimeout(function(pluginType) {
                    return function() {
                        executionHandler('GET', false, pluginType)
                    };
                }(pluginType), 1000);
            }
        };
    }(shwPp, pluginType));
    return false;
}

function loadStatisticsInTable2(data) {
    if (data && data.length > 0) {
        for (var i = 0; i < data.length; i++) {
            var dt = data[i].split("|");
            var trid = 'tr_' + dt[0] + '_' + dt[1];
            if ($('#' + trid).length > 0) {
                var succ = dt[2] * 1 == 1 ? 1 : 0;
                var fail = dt[2] * 1 == 0 ? 1 : 0;
                var osucc = $('#' + trid).attr('data-succ');
                var ofail = $('#' + trid).attr('data-fail');
                succ += osucc * 1;
                fail += ofail * 1;
                $('#' + trid).attr('data-succ', succ);
                $('#' + trid).attr('data-fail', fail);
                $('#' + trid).find('td')[1].innerHTML = (succ + fail);
                $('#' + trid).find('td')[2].innerHTML = succ;
                $('#' + trid).find('td')[3].innerHTML = fail;
            } else {
                var succ = dt[2] * 1 == 1 ? 1 : 0;
                var fail = dt[2] * 1 == 0 ? 1 : 0;
                $('#lol_sts').find('tbody').prepend(')<tr id="' + trid + '" data-succ="' + succ + '" data-fail="' + fail + '"><td style="color:black">' + dt[0] + '-' + dt[1] + '</td><td style="color:black">' + (succ + fail) + '</td><td style="color:black">' + succ + '</td><td style="color:black">' + fail + '</td></tr/>');
            }
        }
    }
}

var currltsnum = 0;

function loadStatisticsInTable(ldata) {
    if (!ldata || ldata.length == 0) return;
    var htm = '';
    for (var i = 0; i < ldata.length; i++) {
        var data = ldata[ldata.length - i - 1];
        if (loadTestData.hdr) {
            loadTestData.hdr.recordValue(data[5][3]);
            //loadTestData.points.push({x:loadtestingdatatablecnt-1, data[5][3]});
            loadTestData.points.push([new Date(data[4]), data[5][3]]);
        }
    }
    loadTestData.dyg.updateOptions({
        'file': loadTestData.points
    });
    loadtestingdatatablecnt += ldata.length;
    $('#hdrstats').html();
    var len = ldata.length > 100 ? 100 : ldata.length;
    for (var i = 0; i < len; i++) {
        var data = ldata[ldata.length - i - 1];
        var url = loadtestingdatatablecnt - i;
        if (data[3] == null)
            url = 'L#' + url;
        else
            url = data[3] + '#' + url;
        if (data.url != null)
            url = '<a href="/reports/' + data[2] + '" click-event="openWind(this)">' + url + '</a>';
        htm += '<tr><td style="color:black">' + url + '</td><td style="color:black">' + data[5][0] + '</td><td style="color:black">' + data[5][1] + '</td><td style="color:black">' + data[5][2] + '</td><td style="color:black">' + data[5][3] + '</td></tr/>';
        currltsnum++;
    }
    $('#lol_tblcu').find('tbody').prepend(htm);
    if (currltsnum > 100) {
        for (var i = 0; i < currltsnum - 100; i++) {
            $('#lol_tblcu').find('tbody tr').last().remove();
        }
        currltsnum = 100;
    }
}

function openWind(ele) {
	window.open(ele.href,'_blank');
	return false;
}

function execSelectedFileTests(testfilen) {
    if (testfilen != null) {
        execFiles = new Array();
        execFiles.push(testfilen);
    }
    calledbytestfpage = true;
    executeHtml('executor');
    executionHandler('PUT', true, 'executor');
    execFiles = new Array();
    return false;
}

function addRemoveExecFile(ele, testfilen) {
    if (ele.checked) {
        execFiles.push(testfilen);
    } else {
        for (var i = execFiles.length; i--;) {
            if (execFiles[i] === testfilen) {
                execFiles.splice(i, 1);
                break;
            }
        }
    }
}

var calledbytestfpage = false;

function triggerClick(ele) {
	ele.trigger('click');
}

function addTcFileHTml() {
    var htmm = '<input type="text" id="tcfile_name_holder_add">&nbsp;&nbsp;<a href="#" class="plusminuslist" click-event=\"manageTcFileHandler(\'POST\', $(\'#tcfile_name_holder_add\').val(),\'\')\">Add Testcase File</a><br/></br/>';
    htmm += '<table border="1">';
    for (var i = 0; i < alltestcasefiles.length; i++) {
        var tcid = 'tcf_' + i;
        htmm += '<tr><td><input type="checkbox" click-event="addRemoveExecFile(this,\'' + alltestcasefiles[i] + '\')"></td><td><a href="#" id="' + tcid + '" class="asideLink1" click-event="triggerClick($(\'#tcfile_' + i + '\'))">' + alltestcasefiles[i] + '</a><input id="inp_' + tcid + '" type="text" style="display:none" value="' + alltestcasefiles[i] + '" blur-event="manageTcFileHandler(\'PUT\', $(\'#' + tcid + '\').html(), this.value)"/></td><td><a href="#" click-event="manageRenameFile(\'' + tcid + '\')">Rename</a></td><td><a href="#" click-event="manageTcFileHandler(\'DELETE\', $(\'#' + tcid + '\').html(),\'\')">X</a></td><td><center><button click-event="execSelectedFileTests(\'' + alltestcasefiles[i] + '\')">Execute</button></center></td></tr>';
    }
    htmm += '</table><br/><center><button click-event="execSelectedFileTests(null)">Execute Selected</button></center>';
    $('#ExampleBeanServiceImpl_form').html(htmm);
	initEvents($('#ExampleBeanServiceImpl_form'));
    $('#heading_main').html('Manage Tests');
}

function manageRenameFile(tcid) {
    $('#' + tcid).hide();
    $('#inp_' + tcid).show();
    return false;
}

function manageTcFileHandler(method, tcFileName, tcFileNameTo) {
    ajaxCall(true, method, "testcasefiles?testcaseFileName=" + tcFileName + "&testcaseFileNameTo=" + tcFileNameTo, "", "", {}, function(data) {
        alert(data);
        startInitConfigTool(addTcFileHTml);
    }, function(data) {
        alert(data);
        startInitConfigTool(addTcFileHTml);
    });
    return false;
}

function testcasesHandler(method, index) {
    ajaxCall(true, method, "testcases?testcaseFileName=" + currtestcasefile, "", "", {
        "testcasename": currtestcases[index + 1]
    }, function(data) {
        alert(data);
        startInitConfigTool(testcasefileView);
    }, null);
    return false;
}

function testcasefileView() {
    for (var i = 0; i < alltestcasefiles.length; i++) {
        if (currtestcasefile == alltestcasefiles[i]) {
            $('#tcfile_' + i).trigger("click");
        }
    }
}

function startInitConfigTool(func) {
    ajaxCall(true, "GET", "testcasefiles", "", "", {}, function(func) {
        return function(data) {
            alltestcasefiles = data;
            $('#testcasefile-holder').html('');
            $('#testcasefile-holder').append('<a href="#" click-event="addTcFileHTml()" class="list-group-item asideLink">&nbsp;Manage Testcase Files</a>');

            var filesGrps = [];
            for (var t = 0; t < data.length; t++) {
                var testname = data[t];
                var folder = "";
                var fileName = testname;
                if (testname.indexOf("\\") != -1) {
                    var folder = testname.substring(0, testname.lastIndexOf("\\"));
                    var fileName = testname.substring(testname.lastIndexOf("\\") + 1);
                }
                if (filesGrps[folder] == undefined) {
                    filesGrps[folder] = [];
                }
                filesGrps[folder].push({
                    fileName: fileName,
                    completeName: testname
                });
            }

            filesGrps.sort();
            var tind = 0;
            for (var folder in filesGrps) {
                if (filesGrps.hasOwnProperty(folder)) {

                    filesGrps[folder].sort();
                    if (folder != "") {
                        var fid = 'folder_' + tind;
                        $('#testcasefile-holder').append('<a status="hide" id="' + fid + '" href="#" class="list-group-item asideLink">&nbsp;<u>' + folder + '</u></a>');
                        $('#' + fid).attr('folder', folder);
                        $('#' + fid).click(function() {
                            var escapedfolder = $(this).attr('folder').replace(/\\/g, '').replace(/-/g, '').replace(/\./g, '');
                            if ($(this).attr('status') == "show") {
                                $('.' + escapedfolder + '_claz').hide();
                                $(this).attr('status', 'hide');
                            } else {
                                $('.' + escapedfolder + '_claz').show();
                                $(this).attr('status', 'show');
                            }
                            return false;
                        });
                    }

                    for (var t = 0; t < filesGrps[folder].length; t++, tind++) {
                        var testFileName = filesGrps[folder][t].completeName;
                        var fileName = filesGrps[folder][t].fileName;
                        var id = 'tcfile_' + tind;
                        if (firstFile == '')
                            firstFile = id;
                        if (folder != "") {
                            var escapedfolder = folder.replace(/\\/g, '').replace(/-/g, '').replace(/\./g, '');
                            $('#testcasefile-holder').append('<a style="margin-left:20px;display:none" id="' + id + '" href="#" class="list-group-item asideLink ' + escapedfolder + '_claz">&nbsp;' + fileName + '</a>');
                        } else {
                            $('#testcasefile-holder').append('<a id="' + id + '" href="#" class="list-group-item asideLink">&nbsp;' + testFileName + '</a>');
                        }
                        $('#' + id).attr('tcfname', testFileName);
                        $('#' + id).click(function() {
                            $('#heading_main').html('Manage Tests');
                            currtestcasefile = $(this).attr('tcfname');
                            currtestcases = [''];
                            ajaxCall(true, "GET", "testcases?testcaseFileName=" + currtestcasefile, "", "", {}, function(data1) {
                                var htmm = '<button class="plusminuslist" click-event=\"addTestCase(true, null, \'\', null, false, false)\">Add New Testcase</button><br/></br/>';
                                if (currtestcasefile.toLowerCase().endsWith(".sel")) {
                                    htmm = "";
                                    $('#ExampleBeanServiceImpl_form').html('<textarea id="req-txtarea" rows=30 style="width:90%">' + data1 + '</textarea>');
                                    prepareForm("testcases?testcaseFileName=" + currtestcasefile + "&configType=", "POST", "Update", "onsucctcnmupdt", null, true, "sel_test_case");
                                    initEvents($('#ExampleBeanServiceImpl_form'));
									$('#ExampleBeanServiceImpl_form').append('<button id="play_test_case" type="submit" class="postbigb" type="submit">Test</button><br/><div id="play_result_area"></div>');
                                    $('#ExampleBeanServiceImpl_form').append('<button id="debug_test_case" type="submit" class="postbigb" type="submit">Debug</button><br/><div id="debug_result_area"></div>');
                                    $('#play_test_case').click(function() {
                                        playTest(currtestcasefile, "", false, false);
                                        return false;
                                    });
                                    $('#debug_test_case').click(function() {
                                        debugTest(currtestcasefile, "", false, false);
                                        return false;
                                    });
                                    return;
                                }
                                if (data1 != null && data1.length > 0) {
                                    htmm += '<table border="1">';
                                    for (var t1 = 0; t1 < data1.length; t1++) {
                                        var tcid = 'tc_' + t1;
                                        htmm += '<tr><td><b class="' + data1[t1]["method"].toLowerCase() + 'big">' + data1[t1]["method"] + '</b></td><td><a href="#" id="' + tcid + '">' + data1[t1]["name"] + '</a></td><td><a href="#" click-event="testcasesHandler(\'DELETE\', ' + t1 + ')">X</a></td></tr>';
                                        currtestcases.push(data1[t1]["name"]);
                                    }
                                    htmm += '</table>';
                                    $('#ExampleBeanServiceImpl_form').html(htmm);
                                    for (var t1 = 0; t1 < data1.length; t1++) {
                                        var tcid = 'tc_' + t1;
                                        $('#' + tcid).attr('tcfname', currtestcasefile);
                                        document.getElementById(tcid).data = data1[t1];
                                        $('#' + tcid).click(function() {
                                            addTestCase(false, this.data, '', $('#' + tcid).attr('tcfname'), false, false);
                                            return false;
                                        });
                                    }
                                } else {
                                    $('#ExampleBeanServiceImpl_form').html(htmm);
                                }
                                return false;
                            }, null);
                        });
                    }
                }
            }
            if (typeof func == "function") func();
        };
    }(func), null);
}

function onsucctcnmupdt() {
    var tc = $('input[name="name"]').val();
    var ac = $('#ExampleBeanServiceImpl_form').attr("action");
    ac = ac.substring(0, ac.lastIndexOf("=") + 1) + tc;
    $('#ExampleBeanServiceImpl_form').attr("action", ac);
    $('#93be7b20299b11e281c10800200c9a66_URL').val(ac);
}

function addTestCase(isNew, data, configType, tcfname, isServerLogsApi, isExternalLogsApi) {
    countMap = {};
    if (tcfname == null)
        tcfname = currtestcasefile;
    if (isNew) {
        document.getElementById('ExampleBeanServiceImpl_form').innerHTML = generateFromValue(schema, '', true, '', '', data, false, true, true, '');
        prepareForm('testcases?testcaseFileName=' + tcfname + '&configType=' + configType, 'POST', 'Add', "onsucctcnmupdt", null);
		initEvents($('#ExampleBeanServiceImpl_form'));
    } else {
        document.getElementById('ExampleBeanServiceImpl_form').innerHTML = generateFromValue(schema, '', true, '', '', data, false, true, true, '');
        prepareForm('testcases?testcaseFileName=' + tcfname + '&configType=' + configType + '&tcName=' + data["name"], 'PUT', 'Update', "onsucctcnmupdt", null);
        initEvents($('#ExampleBeanServiceImpl_form'));
        if (tcfname != null && data != null) {
            $('#ExampleBeanServiceImpl_form').append('<button id="play_test_case" type="submit" class="postbigb" type="submit">Test</button><br/><div id="play_result_area"></div>');
            $('#play_test_case').click(function(tcfname, name, isServerLogsApi, isExternalLogsApi) {
                return function() {
                    playTest(tcfname, name, isServerLogsApi, isExternalLogsApi);
                    return false;
                }
            } (tcfname, data["name"], isServerLogsApi, isExternalLogsApi));
            if (tcfname.toLowerCase().endsWith(".sel"))  {
            	$('#ExampleBeanServiceImpl_form').append('<button id="debug_test_case" type="submit" class="postbigb" type="submit">Debug</button><br/><div id="debug_result_area"></div>');
	            $('#debug_test_case').click(function(tcfname, name, isServerLogsApi, isExternalLogsApi) {
	                return function() {
	                    debugTest(tcfname, name, isServerLogsApi, isExternalLogsApi);
	                    return false;
	                }
	            } (tcfname, data["name"], isServerLogsApi, isExternalLogsApi));
	        }
        }
    }
}

function playTest(tcf, tc, isServerLogsApi, isExternalLogsApi) {
    var isserverlogfile = isServerLogsApi ? "&isServerLogsApi=true" : "";
    isserverlogfile += isExternalLogsApi ? "&isExternalLogsApi=true" : "";
    ajaxCall(true, "PUT", "/reports?action=playTest&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
        return function(data) {
            if (tcf.toLowerCase().endsWith(".sel")) {
                $("#myModalB").html('Test Report for script ' + tcf);
                $("#myModalB").html('<iframe src="reports/selenium-index.html" style="width:100%;height:500px;border:none;"></iframe>');
                $("#myModal").modal();
                return;
            }
            var content = getTestResultContent1(data);
            $('#play_result_area').html(content);
        };
    }(tcf), null);
}

const uid = function() {
    return Date.now().toString(36) + Math.random().toString(36).substr(2);
}

var ceeditor, prevline, chkIntv, sessionId;
function debugTest(tcf, tc, isServerLogsApi, isExternalLogsApi) {
	var dbgctrl = $('#req-txtarea').width() + $('#req-txtarea').offset().left - 100;
	if(!sessionId) sessionId = uid();
	var isserverlogfile = "&sessionId="+sessionId;
	$('#req-txtarea').data('tcf', tcf);
	ceeditor = CodeMirror.fromTextArea(document.getElementById('req-txtarea'), {
		lineNumbers: true,
		tabSize: 4,
		matchBrackets: true,
		mode: 'text/x-perl',
		gutters: ["CodeMirror-linenumbers", "breakpoints"]
	});
	ceeditor.on("gutterClick", function(cm, n) {
		function makeMarker() {
			var marker = document.createElement("div");
			marker.style.color = "#822";
			marker.innerHTML = "●";
			return marker;
		}
		var info = cm.lineInfo(n);
		if(info.gutterMarkers) {
			ajaxCall(true, "PUT", "/reports?action=debug&line=r"+n+"&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
		        return function(data) {
		        	if(data.startsWith("Fail: ")) {
		        		alert(data);
		        	} else {
		        		cm.setGutterMarker(n, "breakpoints", info.gutterMarkers ? null : makeMarker());
		        	}
		        };
		    }(tcf), null);
		} else {
			ajaxCall(true, "PUT", "/reports?action=debug&line="+n+"&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
		        return function(data) {
		        	if(data.startsWith("Fail: ")) {
		        		alert(data);
		        	} else {
		        		cm.setGutterMarker(n, "breakpoints", info.gutterMarkers ? null : makeMarker());
		        	}
		        };
		    }(tcf), null);
		}
	});
	ceeditor.setOption("extraKeys", {
		F6: function(cm) {
			ajaxCall(true, "PUT", "/reports?action=debug&line=-1&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
		        return function(data) {
		        	if(data.startsWith("Fail: ")) {
		        		alert(data);
		        	} else {
		        		line = data.replace("Success: ", "")*1;
		        		ceeditor.removeLineClass(prevline, 'wrap', 'CodeMirror-activeline-background');
		        		prevline = line-1;
		        		ceeditor.addLineClass(line-1, 'wrap', 'CodeMirror-activeline-background');
		        	}
		        };
		    }(tcf), null);
	  	},
	  	F8: function(cm) {
	    	ajaxCall(true, "PUT", "/reports?action=debug&line=-2&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
		        return function(data) {
		        	if(data.startsWith("Fail: ")) {
		        		alert(data);
		        	} else {
		        		line = data.replace("Success: ", "")*1;
		        		ceeditor.removeLineClass(prevline, 'wrap', 'CodeMirror-activeline-background');
		        		prevline = line-1;
		        		ceeditor.addLineClass(line-1, 'wrap', 'CodeMirror-activeline-background');
		        	}
		        };
		    }(tcf), null);
	  	},
	  	'Ctrl-C': function(cm) {
	    	ajaxCall(true, "PUT", "/reports?action=debug&line=-3&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
		        return function(data) {
		        	if(data.startsWith("Fail: ")) {
		        		alert(data);
		        	} else {
		        		line = data.replace("Success: ", "")*1;
		        		ceeditor.removeLineClass(prevline, 'wrap', 'CodeMirror-activeline-background');
		        		prevline = line-1;
		        		ceeditor.addLineClass(line-1, 'wrap', 'CodeMirror-activeline-background');
		        	}
		        };
		    }(tcf), null);
	  	},
	  	'Ctrl-X': function(cm) {
	    	ajaxCall(true, "PUT", "/reports?action=debug&line=-4&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
		        return function(data) {
		        	if(data.startsWith("Fail: ")) {
		        		alert(data);
		        	} else {
		        		ceeditor.removeLineClass(prevline, 'wrap', 'CodeMirror-activeline-background');
		        		prevline = 0;
		        	}
		        };
		    }(tcf), null);
	  	}
	});
	/*function cmkp() {
		if(!ceeditor) return;
	    var key = event.keyCode;
	    if(key == 117) {
	    	ajaxCall(true, "PUT", "/reports?action=debug&line=-1&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
		        return function(data) {
		        	if(data.startsWith("Fail: ")) {
		        		alert(data);
		        	} else {
		        		line = data.replace("Success: ", "")*1;
		        		ceeditor.removeLineClass(prevline, 'wrap', 'CodeMirror-activeline-background');
		        		prevline = line-1;
		        		ceeditor.addLineClass(line-1, 'wrap', 'CodeMirror-activeline-background');
		        	}
		        };
		    }(tcf), null);
	    } else if(key == 119) {
	    	ajaxCall(true, "PUT", "/reports?action=debug&line=-2&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
		        return function(data) {
		        	if(data.startsWith("Fail: ")) {
		        		alert(data);
		        	} else {
		        		line = data.replace("Success: ", "")*1;
		        		ceeditor.removeLineClass(prevline, 'wrap', 'CodeMirror-activeline-background');
		        		prevline = line-1;
		        		ceeditor.addLineClass(line-1, 'wrap', 'CodeMirror-activeline-background');
		        	}
		        };
		    }(tcf), null);
	    } else if (event.ctrlKey) {
	    	if (key === ('C').charCodeAt(0) - 64) {
	    		ajaxCall(true, "PUT", "/reports?action=debug&line=-3&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
			        return function(data) {
			        	if(data.startsWith("Fail: ")) {
			        		alert(data);
			        	} else {
			        		line = data.replace("Success: ", "")*1;
			        		ceeditor.removeLineClass(prevline, 'wrap', 'CodeMirror-activeline-background');
			        		prevline = line-1;
			        		ceeditor.addLineClass(line-1, 'wrap', 'CodeMirror-activeline-background');
			        	}
			        };
			    }(tcf), null);
	    	}
	        if (key === ('X').charCodeAt(0) - 64) {
	        	ajaxCall(true, "PUT", "/reports?action=debug&line=-4&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
			        return function(data) {
			        	if(data.startsWith("Fail: ")) {
			        		alert(data);
			        	} else {
			        		ceeditor.removeLineClass(prevline, 'wrap', 'CodeMirror-activeline-background');
			        		prevline = 0;
			        	}
			        };
			    }(tcf), null);
	        }
	    }
	}
	document.onkeypress = cmkp;*/
	ajaxCall(true, "PUT", "/reports?action=debug&line=0&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
        return function(data) {
        	if(data.startsWith("Fail: ")) {
        		alert(data);
        	} else {
        		$('#debug-controls').css('left', dbgctrl + 'px');
        		$('#debug-controls').removeClass('hidden');
        		$('#debug-controls').html('<i key="F8" class="glyphicon glyphicon-play"></i><i key="Ctrl-C" class="glyphicon glyphicon-stop"></i><i key="F6" class="glyphicon glyphicon-step-forward"></i><i key="Ctrl-X" class="glyphicon glyphicon-remove-circle"></i>');
        		$('#debug-controls').find('i').css('width', '25px');
        		$('#debug-controls').find('i').css('font-size', 'large');
        		$('#debug-controls').find('i').css('color', 'indianred');
        		$('#debug-controls').find('i').on('click', function() {
        			ceeditor.options.extraKeys[$(this).attr('key')]();
        		});
        		ceeditor.getDoc().setValue(data.replace("Success: ", ""));
        		prevline = 0;
        		ceeditor.addLineClass(0, 'wrap', 'CodeMirror-activeline-background');
        		chkIntv = setInterval(function() {
        			if(!$('#req-txtarea').data('tcf')) {
        				clearInterval(chkIntv);
        				ceeditor = undefined;
        				$('#debug-controls').addClass('hidden');
				        //document.removeEventListener('keypress',  cmkp);
        			}
			    	ajaxCall(false, "PUT", "/reports?action=debug&line=-5&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
				        return function(data) {
				        	if(data.startsWith("Fail: ")) {
				        		alert(data);
				        		$('#debug-controls').addClass('hidden');
				        		clearInterval(chkIntv);
				        		ceeditor = undefined;
				        	} else {
				        		if(data=="Success: 0") {
				        			alert("Debug session completed");
				        			$('#debug-controls').addClass('hidden');
									clearInterval(chkIntv);
									$('a.asideLink[tcfname="'+tcf+'"]').trigger('click');
				        			ceeditor = undefined;
				        			//document.removeEventListener('keypress',  cmkp);
				        		} else {
					        		line = data.replace("Success: ", "")*1;
					        		ceeditor.removeLineClass(prevline, 'wrap', 'CodeMirror-activeline-background');
					        		prevline = line-1;
					        		ceeditor.addLineClass(line-1, 'wrap', 'CodeMirror-activeline-background');
				        		}
				        	}
				        };
				    }(tcf), null);
			    }, 5000);
        	}
        };
    }(tcf), null);
}

function initiateDownloads() {
    ajaxCall(true, "GET", "/projectZip", "", "", {}, function(data) {
        var content = "<br/>";
        if (data[0] == "binary")
            content += "<a href=\"/gatf-test-bin.zip\"><h3>Binary Project</h3></a><br/><br/>";
        else if (data[0] == "maven")
            content += "<a href=\"/gatf-test-bin.zip\"><h3>Maven Project</h3></a><br/><br/>";
        if (data.length > 1)
            content += "<a href=\"/gatf-test-mvn.zip\"><h3>Maven Project</h3></a><br/><br/>";
        $('#ExampleBeanServiceImpl_form').html(content);
    }, function(data) {
        var content = "<p>Error while generating projects..\n" + data + "</p><br/><br/>";
        $('#ExampleBeanServiceImpl_form').html(content);
    });
    return false;
}

function syntaxHighlight1(json) {
    json = json.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    return json.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, function(match) {
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

function escapeHtml1(s) {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function printResponse1(msg, contentTypeHeader) {
    var resp = msg;
    if (contentTypeHeader != null && contentTypeHeader.indexOf('application/json') != -1) {
        resp = syntaxHighlight1(msg);
    } else if (contentTypeHeader != null && contentTypeHeader.indexOf('xml') != -1) {
        resp = vkbeautify.xml(resp);
        resp = escapeHtml1(resp);
        resp = prettyPrintOne(resp);
    }
    return resp;
}

function getData1(value, contentType) {
    if (undefined == value || value == null || value == '')
        return 'NA';
    else {
        var jsonobj = null;
        try {
            jsonobj = JSON.parse(value);
            value = JSON.stringify(jsonobj, undefined, 4);
            contentType = 'application/json';
        } catch (e) {
            //console.error("Parsing error:", e); 
        }
        return printResponse1(value, contentType);
    }
}

function getTestResultContent1(report) {
    var reqhdrsVal = getData1(report.requestHeaders, 'text/plain');

    var rescnttyp = 'text/plain';
    if (undefined != report.responseContentType && report.responseContentType != null)
        rescnttyp = report.responseContentType;

    var responseHeaders = getData1(report.responseHeaders, 'text/plain');

    var reqcnttyp = 'text/plain';
    if (undefined != report.requestContentType && report.requestContentType != null)
        reqcnttyp = report.requestContentType;

    var content = '<div><p>Request:</p><pre>Actual Url: ' + report.actualUrl + '</pre><pre>Template Url: ' + report.url + '</pre><pre>' + reqhdrsVal + '</pre>';
    content += '<pre>' + getData1(report.requestContent, reqcnttyp) + '</pre></div>';
    content += '<div><p>Response:</p><pre>' + responseHeaders + '</pre>';
    if (report.responseStatusCode != undefined) content += '<pre>Status Code: ' + report.responseStatusCode + '</pre>';
    if (report.serverLogs != undefined) content += '<pre>Server Log: ' + report.serverLogs + '</pre>';
    content += '<pre>' + getData1(report.responseContent, rescnttyp) + '</pre></div>';

    var error = getData1(report.error, 'text/plain');
    if (undefined != report.errors && report.errors != null) {
        var err = '';
        for (var key in report.errors) {
            if (report.errors.hasOwnProperty(key)) {
                err += ('Run ' + key + "\n" + report.errors[key] + "\n\n");
            }
        }
        if (err != '')
            error = err;
    }
    if (error != 'NA') {
        error = escapeHtml(error);
        content += '<div><p>Errors:</p><pre>' + error + '</pre>';
        content += '<pre>' + escapeHtml(getData1(report.errorText, 'text/plain')) + '</pre></div>';
    }
    return content;
}

function generatorConfig() {
    $('#heading_main').html('Generator Configurationuration');
    var configschema = JSON.parse('{"type":"object","properties":{"testPaths":{"type":"array","items":{"nolabel":true,"type":"string"}},"soapWsdlKeyPairs":{"type":"array","items":{"nolabel":true,"type":"string"}},"urlPrefix":{"type":"string"},"requestDataType":{"type":"string"},"responseDataType":{"type":"string"},"resourcepath":{"type":"string"},"enabled":{"type":"boolean","required":true},"overrideSecure":{"type":"boolean","required":true},"useSoapClient":{"type":"boolean","required":true},"urlSuffix":{"type":"string"},"postmanCollectionVersion":{"type":"integer"},"testCaseFormat":{"type":"string"}}}');
    ajaxCall(true, "GET", "configure?configType=generator", "", "", {}, function(configschema) {
        return function(data) {
            countMap = {};
            $('#ExampleBeanServiceImpl_form').html(generateFromValue(configschema, '', true, '', '', data, false, true, true, ''));
            prepareForm('configure?configType=generator', 'POST', jQuery.isEmptyObject(data) ? 'Add' : 'Update', null, null);
			initEvents($('#ExampleBeanServiceImpl_form'));
        };
    }(configschema), null);
}

function configuration() {
    $('#heading_main').html('Manage Configuration');
    var configschema = {
        "type": "object",
        "properties": {
            "baseUrl": {
                "type": "string"
            },
            "testCasesBasePath": {
                "type": "string"
            },
            "testCaseDir": {
                "type": "string"
            },
            "outFilesBasePath": {
                "type": "string"
            },
            "outFilesDir": {
                "type": "string"
            },
            "authEnabled": {
                "type": "boolean",
                "required": true
            },
            "authUrl": {
                "type": "string"
            },
            "authExtractAuth": {
                "type": "string"
            },
            "authParamsDetails": {
                "type": "string"
            },
            "wsdlLocFile": {
                "type": "string"
            },
            "soapAuthEnabled": {
                "type": "boolean",
                "required": true
            },
            "soapAuthWsdlKey": {
                "type": "string"
            },
            "soapAuthOperation": {
                "type": "string"
            },
            "soapAuthExtractAuth": {
                "type": "string"
            },
            "numConcurrentExecutions": {
                "type": "integer"
            },
            "httpCompressionEnabled": {
                "type": "boolean",
                "required": true
            },
            "httpConnectionTimeout": {
                "type": "integer"
            },
            "httpRequestTimeout": {
                "type": "integer"
            },
            "concurrentUserSimulationNum": {
                "type": "integer"
            },
            "testDataConfigFile": {
                "type": "string"
            },
            "authDataProvider": {
                "type": "string",
                "defaultIndex": 1,
                "enumVar": "miscMap['providers']"
            },
            "compareEnabled": {
                "type": "boolean",
                "required": true
            },
            "enabled": {
                "type": "boolean"
            },
            "loadTestingEnabled": {
                "type": "boolean",
                "required": true
            },
            "loadTestingTime": {
                "type": "number"
            },
            "distributedLoadTests": {
                "type": "boolean",
                "required": true
            },
            "compareBaseUrlsNum": {
                "type": "integer"
            },
            "concurrentUserRampUpTime": {
                "type": "number"
            },
            "loadTestingReportSamples": {
                "type": "integer"
            },
            "repeatSuiteExecutionNum": {
                "type": "integer"
            },
            "isGenerateExecutionLogs": {
                "type": "boolean",
                "required": false
            },
            "isSeleniumExecutor": {
                "type": "boolean",
                "required": false
            },
            "isSeleniumModuleTests": {
                "type": "boolean",
                "required": false
            },
            "seleniumScripts": {
                "type": "array",
                "items": {
                    "nolabel": true,
                    "type": "string"
                }
            },
            "seleniumLoggerPreferences": {
                "type": "string"
            },
            "javaHome": {
                "type": "string"
            },
            "gatfJarPath": {
                "type": "string"
            },
            "seleniumDriverConfigs": {
                "label": {
                    "type": "section",
                    "value": "Selenium Driver Configurations"
                },
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "name": {
                            "type": "string"
                        },
                        "version": {
                            "type": "string"
                        },
                        "platform": {
                            "type": "string"
                        },
                        "driverName": {
                            "type": "string"
                        },
                        "path": {
                            "type": "string"
                        },
                        "capabilities": {
                            "label": {
                                "type": "section",
                                "value": "Capabilities"
                            },
                            "type": "map",
                            "types": {
                                "key": {
                                    "type": "string"
                                },
                                "value": {
                                    "type": "string"
                                }
                            }
                        },
                        "properties": {
                            "label": {
                                "type": "section",
                                "value": "Properties"
                            },
                            "type": "map",
                            "types": {
                                "key": {
                                    "type": "string"
                                },
                                "value": {
                                    "type": "string"
                                }
                            }
                        }
                    }
                }
            },
            "debugEnabled": {
                "type": "boolean",
                "required": true
            },
            "isOrderByFileName": {
                "type": "boolean",
                "required": true
            },
            "isServerLogsApiAuthEnabled": {
                "type": "boolean",
                "required": true
            },
            "serverLogsApiAuthExtractAuth": {
                "type": "string"
            },
            "isFetchFailureLogs": {
                "type": "boolean",
                "required": true
            },
            "testCaseHooksPaths": {
                "type": "array",
                "items": {
                    "nolabel": true,
                    "type": "string"
                }
            },
            "distributedNodes": {
                "label": {
                    "type": "section",
                    "value": "Distributed Load Test Nodes"
                },
                "type": "array",
                "items": {
                    "nolabel": true,
                    "type": "string"
                }
            },
            "ignoreFiles": {
                "type": "array",
                "items": {
                    "nolabel": true,
                    "type": "string"
                }
            },
            "orderedFiles": {
                "type": "array",
                "items": {
                    "nolabel": true,
                    "type": "string",
                    "enumVar": "alltestcasefiles"
                }
            },
            "gatfTestDataConfig": {
                "type": "object",
                "properties": {
                    "globalVariables": {
                        "label": {
                            "type": "section",
                            "value": "Global Test Variables"
                        },
                        "type": "map",
                        "types": {
                            "key": {
                                "type": "string"
                            },
                            "value": {
                                "type": "string"
                            }
                        }
                    },
                    "compareEnvBaseUrls": {
                        "label": {
                            "type": "section",
                            "value": "Comparison Environments"
                        },
                        "type": "array",
                        "items": {
                            "nolabel": true,
                            "type": "string"
                        }
                    },
                    "dataSourceList": {
                        "label": {
                            "type": "section",
                            "value": "Data Sources"
                        },
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "dataSourceName": {
                                    "type": "string"
                                },
                                "dataSourceClass": {
                                    "type": "string",
                                    "enumVar": "miscMap['datasourcecls']"
                                },
                                "poolSize": {
                                    "type": "integer"
                                },
                                "newResourceCheckoutTimeMs": {
                                    "type": "number"
                                },
                                "args": {
                                    "type": "array",
                                    "items": {
                                        "nolabel": true,
                                        "type": "string"
                                    }
                                }
                            }
                        }
                    },
                    "dataSourceListForProfiling": {
                        "label": {
                            "type": "section",
                            "value": "Data Sources For Profiling"
                        },
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "dataSourceName": {
                                    "type": "string"
                                },
                                "dataSourceClass": {
                                    "type": "string",
                                    "enumVar": "miscMap['datasourcecls']"
                                },
                                "poolSize": {
                                    "type": "integer"
                                },
                                "newResourceCheckoutTimeMs": {
                                    "type": "number"
                                },
                                "args": {
                                    "type": "array",
                                    "items": {
                                        "nolabel": true,
                                        "type": "string"
                                    }
                                }
                            }
                        }
                    },
                    "providerTestDataList": {
                        "label": {
                            "type": "section",
                            "value": "Test Data Providers"
                        },
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "dataSourceName": {
                                    "type": "string",
                                    "enumVar": "miscMap['datasources']"
                                },
                                "providerName": {
                                    "type": "string"
                                },
                                "providerClass": {
                                    "type": "string",
                                    "enumVar": "miscMap['providercls']"
                                },
                                "sourceProperties": {
                                    "type": "string"
                                },
                                "providerProperties": {
                                    "type": "string"
                                },
                                "queryStr": {
                                    "type": "string"
                                },
                                "enabled": {
                                    "type": "boolean"
                                },
                                "args": {
                                    "type": "array",
                                    "items": {
                                        "nolabel": true,
                                        "type": "string"
                                    }
                                },
                                "live": {
                                    "type": "boolean"
                                }
                            }
                        }
                    },
                    "dataSourceHooks": {
                        "label": {
                            "type": "section",
                            "value": "Data Source Hooks"
                        },
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "hookName": {
                                    "type": "string"
                                },
                                "hookClass": {
                                    "type": "string"
                                },
                                "dataSourceName": {
                                    "type": "string",
                                    "enumVar": "miscMap['datasources']"
                                },
                                "queryStrs": {
                                    "type": "array",
                                    "items": {
                                        "nolabel": true,
                                        "type": "string"
                                    }
                                },
                                "executeOnStart": {
                                    "type": "boolean",
                                    "required": true
                                },
                                "executeOnShutdown": {
                                    "type": "boolean",
                                    "required": true
                                }
                            }
                        }
                    }
                },
                "name": "GatfTestDataConfig"
            },
            "wrkPath": {
                "type": "string"
            },
            "wrk2Path": {
                "type": "string"
            },
            "vegetaPath": {
                "type": "string"
            },
            "autocannonPath": {
                "type": "string"
            }
        }
    };
    ajaxCall(true, "GET", "configure?configType=executor", "", "", {}, function(configschema) {
        return function(data) {
            countMap = {};
            isSeleniumExecutor = data.isSeleniumExecutor;
            $('#ExampleBeanServiceImpl_form').html(generateFromValue(configschema, '', true, '', '', data, false, true, true, ''));
            prepareForm('configure?configType=executor', 'POST', jQuery.isEmptyObject(data) ? 'Add' : 'Update', "startInitConfigTool", null);
			initEvents($('#ExampleBeanServiceImpl_form'));
        };
    }(configschema), null);
}
var isSeleniumExecutor = false;

function handleAuth(token) {
    if (debugEnabled) alert("Providing authentication access to Test Links....");
    $('.asideLink').each(function() {
        var lnkupd = "?" + authTokNm + "=";
        if (typeof authTokNm != "undefined" && typeof authTokTyp != "undefined") {
            if (this.href.indexOf(lnkupd) == -1) {
                this.href += lnkupd + token;
            } else {
                var tempbefore = this.href.substr(0, this.href.indexOf(lnkupd));
                var tempafter = this.href.substr(this.href.indexOf(lnkupd));
                if (tempafter.indexOf('&') != -1) {
                    tempafter = tempafter.substr(this.href.indexOf('&'));
                } else {
                    tempafter = '';
                }
                this.href = tempbefore + lnkupd + token + tempafter;
            }
        }
    });
}

function togglehideShowClassGroup(clas, check) {
    $('#ExampleBeanServiceImpl_form div').each(function() {

        if (this.className != "" && (this.className == clas || this.className.indexOf(clas + "[") != -1)) {
            if (this.style.display == 'none')
                this.style.display = 'block';
            else
                this.style.display = 'none';
        }
    });
    $('#ExampleBeanServiceImpl_form span').each(function() {

        if (this.className != "" && this.className.indexOf(clas + "[") != -1) {
            if (check.checked)
                this.style.display = 'block';
            else
                this.style.display = 'none';
        }
    });
}

function hideShowClasses(clas1, clas2, check) {
    $('.' + clas1).each(function() {
        if (check.checked)
            this.style.display = 'none';
        else
            this.style.display = 'block';
    });
    $('.' + clas2).each(function() {
        if (check.checked)
            this.style.display = 'block';
        else
            this.style.display = 'none';
    });
}


function addMapKV(label) {
    var tem = document.getElementById(label + '_holder');
    if (tem == null)
        $('#ExampleBeanServiceImpl_form').append('<div id="' + label + '_holder"></div>');
    var temid = label + '_holder';
    var temkey = document.getElementById(label + '+mapkey').value;
    var temval = document.getElementById(label + '+mapvalue').value;
    var inptem = document.getElementById(label + '[' + temkey + ']');
    if (inptem != null) {
        alert('A map entry with the same key already exists!!');
        return;
    }
    document.getElementById(temid).innerHTML += ('<input type="hidden" id="' + label + '[' + temkey + ']" name="' + label + '[\'' + temkey + '\']" value="' + temval + '"/>');
    document.getElementById(temid).innerHTML += ('&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ' + label + ' entry Key:' + temkey);
    document.getElementById(temid).innerHTML += (', ' + label + ' entry Value:' + temval + '<br/>');
}


function addListV(label) {
    var vcounter = 0;
    var tem = document.getElementById(label + '_holder');
    if (tem == null)
        $('#ExampleBeanServiceImpl_form').append('<div id="' + label + '_holder"></div>');
    var temid = label + '_holder';
    var temlval = document.getElementById(label + '_' + vcounter);
    while (temlval != null) {
        vcounter++;
        temlval = document.getElementById(label + '_' + vcounter);
    }
    var temval = document.getElementById(label + '+listv').value;
    document.getElementById(temid).innerHTML += ('<input type="hidden" id="' + label + '_' + vcounter + '" name="' + label + '[' + vcounter + ']" value="' + temval + '"/>');
    vcounter++;
    document.getElementById(temid).innerHTML += ('&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ' + label + ' entry value:' + temval + '<br/>');
}

function addSetV(label) {
    var scounter = 0;
    var tem = document.getElementById(label + '_holder');
    if (tem == null)
        $('#ExampleBeanServiceImpl_form').append('<div id="' + label + '_holder"></div>');
    var temid = label + '_holder';
    var temlval = document.getElementById(label + '_' + vcounter);
    while (temlval != null) {
        scounter++;
        temlval = document.getElementById(label + '_' + scounter);
    }
    var temval = document.getElementById(label + '+setv').value;
    $('.' + label + 'setclass').each(function() {
        if (this.value == temval) {
            alert('A set entry with the same value already exists!!');
            return;
        }
    });
    document.getElementById(temid).innerHTML += ('<input type="hidden" id="' + label + '_' + scounter + '" class="' + label + 'setclass" name="' + label + '[' + scounter + ']" value="' + temval + '"/>');
    scounter++;
    document.getElementById(temid).innerHTML += ('&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ' + label + ' entry value:' + temval + '<br/>');
}

function addFormParm(label) {
    var url = "../" + $('#93be7b20299b11e281c10800200c9a66_URL').val();
    $('#ExampleBeanServiceImpl_form').attr('action', url);

    var temkey = document.getElementById(label + '+name').value;
    var temsel = document.getElementById(label + '+sel').value;
    if (document.getElementById(label + temkey) != null) {
        alert('A parameter with this name already exists!!');
        return;
    }
    if (temsel == "text")
        $('#ExampleBeanServiceImpl_form').append('<label>' + temkey + '&nbsp;</label><input type="text" id="' + label + temkey + '" name="' + temkey + '"/><br/><br/>');
    else {
        $('#ExampleBeanServiceImpl_form').append('<label>' + temkey + '&nbsp;</label><input type="file" id="' + label + temkey + '" name="' + temkey + '"/><br/><br/>');
    }
}

function getReports() {
    ajaxCall(true, "GET", "reports/index.html", "", "", {}, function(data) {
        document.location = "reports/index.html";
    }, function(data) {
        alert("No Reports found...");
    });
    return false;
}

function getSeleniumReports() {
    ajaxCall(true, "GET", "reports/selenium-index.html", "", "", {}, function(data) {
        document.location = "reports/selenium-index.html";
    }, function(data) {
        alert("No Reports found...");
    });
    return false;
}

function searchLeftNavs(ele) {
    var term = ele.target.value.trim();
    $('.accordion-toggle').each(function() {
        if (term == '') {
            $(this).parent().show();
        } else {
            if ($(this).text().search(new RegExp(term, "i")) == -1) {
                $(this).parent().hide();
            }
        }
    });
    $('.accordion-inner').each(function() {
        if (term == '') {
            $(this).parent().show();
            $(this).children().show();
        } else {
            var flag = false;
            $(this).children().each(function() {
                if ($(this).text().search(new RegExp(term, "i")) == -1)
                    $(this).hide();
                else {
                    flag = true;
                    $(this).show();
                }
            });
            if (flag) {
                $(this).parent().show();
                $(this).parent().parent().children().eq(0).show();
            }
        }
    });
}

function ajaxCall(blockUi, meth, url, contType, content, vheaders, sfunc, efunc) {
    if (blockUi) $.blockUI({
        message: '<h3><img src="resources/busy.gif" /> Just a moment...</h3>'
    });
    $.ajax({
        headers: vheaders,
        type: meth,
        processData: false,
        url: url,
        contentType: contType,
        data: content
    }).done(function(msg, statusText, jqXhr) {
        if (blockUi) $.unblockUI();
        var data = jqXhr.responseText;
        try {
            data = JSON.parse(jqXhr.responseText);
        } catch (err) {
            data = jqXhr.responseText;
        }
        sfunc(data);
    }).fail(function(jqXhr, textStatus, msg) {
        if (blockUi) $.unblockUI();
        if (efunc == null) alert(jqXhr.responseText);
        var data = jqXhr.responseText;
        try {
            data = JSON.parse(jqXhr.responseText);
        } catch (err) {
            data = jqXhr.responseText;
        }
        if (efunc != null) efunc(data);
    });
}

function handleChgEvent() {
	hideShowClasses('form-elems','form-request_content', this);
	updatetawidcont();
}

function initEvents(par) {
	par.find('input[mo-event],textarea[mo-event],select[mo-event]').off('mouseover').on('mouseover', function() {
		var evt = $(this).attr('mo-event');
		execFunction(evt, $(this));
	});
	par.find('input[blur-event],textarea[blur-event],select[blur-event]').off('blur').on('blur', function() {
		var evt = $(this).attr('blur-event');
		execFunction(evt, $(this));
	});
	par.find('input[change-event],textarea[change-event],select[change-event]').off('change').on('change', function() {
		var evt = $(this).attr('change-event');
		execFunction(evt, $(this));
	});
}

function execTc(method, succFunc, failFunc) {
	executeTest('#93be7b20299b11e281c10800200c9a66_URL', method.toUpperCase(), 'application/json', '#ExampleBeanServiceImpl_form', succFunc, failFunc);
	$('#api_execution_reponse').show();
	return false;
}

function prepareForm(url, method, buttonLabel, succFunc, failFunc, isSelfContained, eid) {
    var htm = '';
    if (!isSelfContained || isSelfContained === false) {
        htm += '<div class="control-group"> \
					<label>Use Raw Text:&nbsp;</label> \
					<div class="controls"><input id="raw_req_cont_flag" type="checkbox" change-event="handleChgEvent()"/></div> \
				</div>';
    }
    if (isSelfContained === true) {
        htm += '<input id="raw_req_cont_flag" type="checkbox" checked=true class="hidden"/><input id="raw_req_cont_type" type="hidden" value="text/plain"/>';
        //$('#ExampleBeanServiceImpl_form').append('<input id="req-txtarea" type="hidden"/>');
        //req-txtarea').val($('#'+eid).val());
    }
    htm += '<div class="control-group" style="display:none"> \
						<label>url&nbsp; \
						</label> \
						<div class="controls"> \
						<input class="form-control" type ="text" style="width: 70%; height: 30px;" id="93be7b20299b11e281c10800200c9a66_URL" value="' + url + '"/> \
						</div> \
					</div>';
    $('#ExampleBeanServiceImpl_form').prepend(htm);
    if (!isSelfContained || isSelfContained === false) {
        $('.map_key_cls').each(function() {
            if (this.nodeName.toLowerCase() == "input")
                updateMapValueNms(this);
        });
        if ($('#req-txtarea').length == 0) {
            $('#ExampleBeanServiceImpl_form').append('<div class="controls form-request_content"><label> Request Content&nbsp;</label>' +
                '<textarea class="form-control" id="req-txtarea" style="width: 70%;"></textarea></div>');
            $('#req-txtarea').val('');
            $('#req-txtarea').autosize();
            $('#req-txtarea').parent().hide();
        } else {
            $('#req-txtarea').parent().addClass('form-request_content');
            $('#req-txtarea').val('');
            $('#req-txtarea').autosize();
            $('#req-txtarea').parent().hide();
        }
        $('.clsDatePicker').datetimepicker({
            format: 'Y-m-d\\TH:i:s.000\\Z',
            step: 1,
            validateOnBlur: false
        });
    }
    $('#ExampleBeanServiceImpl_form').append(
        '<div class="control-group"> \
				<label class=""></label> \
				<div class="controls"> \
					<button type="submit" class="' + method.toLowerCase() + 'bigb" type="submit" click-event="execTc(\''+method.toLowerCase()+'\', '+succFunc+', '+failFunc+')">' + buttonLabel + '</button> \
				</div> \
			</div> \
			<br/><br/><p></p><br/> \
			<div id="api_execution_reponse" style="display:none"> \
			<label>Response Time&nbsp;</label><span id="restime"></span><br/><br/> \
			<label>Response Headers:&nbsp;</label><div><pre id="reshdrs" style="word-wrap:break"></pre></div> \
			<label style="word-wrap:break-word;margin-left:50px;width:auto;background-color: #ebf4fb;border:none;"></label><div><br/><pre id="status" class="prettyprint" style="word-wrap:break-word;margin-left:50px;width:auto;border:none;"></pre></div><br/></div>');
}