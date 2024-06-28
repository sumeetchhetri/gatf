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

function rgb2hex(ele){
	rgb = $(ele).css('background-color').match(/^rgb\((\d+),\s*(\d+),\s*(\d+)\)$/);
	return ("#" +
		("0" + parseInt(rgb[1],10).toString(16)).slice(-2) +
		("0" + parseInt(rgb[2],10).toString(16)).slice(-2) +
		("0" + parseInt(rgb[3],10).toString(16)).slice(-2)).toLowerCase();
}

var currColor = "#ffffff";
var currTheme = "default";
function darkMode(theme) {
	if (theme==="dark" || (theme!=="default" && rgb2hex($('.header'))=="#ffffff")) {
		localStorage.setItem('theme', 'dark');
		$('body').css('background-color', '#000000');
		$('.header').css('background-color', '#000000');
		$('.header').find('.navbar-collapse').css('background-color', '#000000');
		$('.btn-plus').css('background-color', '#000000');
		$('.panel').css('background-color', '#000000');
		$('.panel').find('.panel-heading').css('background-color', '#000000');
		$('.dropdown-menu').css('background-color', '#000000');
		$('.list-group-item').css('background-color', '#000000');
		$('.navbar-default').css('background-color', '#000000');
		$('body').css('color', '#ffffff');
		$('.header').find('.navbar-collapse').css('color', '#ffffff');
		$('.btn-plus').css('color', '#ffffff');
		$('.panel').css('color', '#ffffff');
		$('.panel').find('.panel-heading').css('color', '#ffffff');
		$('.dropdown-menu').css('color', '#ffffff');
		$('table').find('td').css('background-color', '#000000');
		$('table').find('td').css('color', '#ffffff');
		$('.list-group-item').css('color', '#ffffff');
		$('.navbar-default').css('color', '#ffffff');
		currColor = "#000000";
		if(ceeditor) {
			ceeditor.setOption("theme", 'dracula');
		}
		currTheme = "dracula";
		jss.set('.close',{'color': 'white', 'opacity': '1', 'background-color': '#000000'});
		jss.set('.form-control',{'color': 'white', 'background-color': '#333'});
		jss.set('input',{'color': 'white', 'background-color': '#333'});
		jss.set('textarea',{'color': 'white', 'background-color': '#333'});
		jss.set('button',{'color': 'white', 'background-color': '#333'});
		jss.set('table th',{'color': 'white', 'background-color': '#333', 'border': '1px solid #ddd'});
		jss.set('table td',{'border': '1px solid #ddd'});
		jss.set('a',{'color': 'white'});
		jss.set('.d2h-file-header',{'color': 'white', 'background-color': '#333'});
		jss.set('.d2h-code-line del, .d2h-code-side-line del',{'color': 'black'});
		jss.set('.d2h-code-line ins, .d2h-code-side-line ins',{'color': 'black'});
		jss.set('.d2h-code-side-line',{'color': 'white', 'background-color': '#333'});
		jss.set('.d2h-code-side-linenumber',{'color': 'white', 'background-color': '#333'});
		jss.set('.d2h-del',{'background-color': '#fee8e9'});
		jss.set('.d2h-ins',{'background-color': '#dfd'});
		jss.set('.postbigb',{'background-color': '#333'});
		jss.set('.putbigb',{'background-color': '#333'});
		jss.set('.plusminuslist',{'background-color': '#333'});
		jss.set('.panel-heading',{'border-bottom': '0px black'});
		jss.set('.modal-content',{'border': '1px solid #ddd', 'background-color': '#000000', 'color': '#ffffff'});
		jss.set('.nav-tabs>li.active>a, .nav-tabs>li.active>a:hover, .nav-tabs>li.active>a:focus',{'border': '1px solid #ddd', 'background-color': '#000000', 'color': '#ffffff'});
		jss.set('.blockUI',{'border': '1px solid #ddd', 'background-color': '#000000', 'color': '#ffffff'});
	} else {
		localStorage.setItem('theme', 'default');
		$('body').css('background-color', '#e0e0e0');
		$('.header').css('background-color', '#ffffff');
		$('.header').find('.navbar-collapse').css('background-color', '#ffffff');
		$('.btn-plus').css('background-color', '#ffffff');
		$('.panel').css('background-color', '#ffffff');
		$('.panel').find('.panel-heading').css('background-color', '#ffffff');
		$('.dropdown-menu').css('background-color', '#ffffff');
		$('.list-group-item').css('background-color', '#ffffff');
		$('.navbar-default').css('background-color', '#f4f4f4');
		$('body').css('color', '#000000');
		$('.header').find('.navbar-collapse').css('color', '#000000');
		$('.btn-plus').css('color', '#000000');
		$('.panel').css('color', '#000000');
		$('.panel').find('.panel-heading').css('color', '#000000');
		$('.dropdown-menu').css('color', '#000000');
		$('table').find('td').css('background-color', '#ffffff');
		$('table').find('td').css('color', '#000000');
		$('.list-group-item').css('color', '#000000');
		$('.navbar-default').css('color', '#000000');
		currColor = "#ffffff";
		if(ceeditor) {
			ceeditor.setOption("theme", 'default');
		}
		currTheme = "default";
		jss.set('.close',{'color': 'black', 'opacity': '0.2', 'background-color': '#ffffff'});
		jss.remove('.form-control');
		jss.remove('input');
		jss.remove('button');
		jss.remove('textarea');
		jss.set('table th',{'color': 'white', 'background-color': '#A7C942', 'border': '1px solid #98bf21'});
		jss.set('table td',{'border': '1px solid #98bf21'});
		jss.set('a',{'color': '#428bca'});
		jss.set('.d2h-file-header',{'color': '#000000', 'background-color': '#ffffff'});
		jss.set('.d2h-code-line del, .d2h-code-side-line del',{'color': '#ffb6ba'});
		jss.set('.d2h-code-line ins, .d2h-code-side-line ins',{'color': '#97f295'});
		jss.set('.d2h-code-side-line',{'color': '#000000', 'background-color': '#ffffff'});
		jss.set('.d2h-code-side-linenumber',{'color': '#000000', 'background-color': '#ffffff'});
		jss.set('.d2h-del',{'background-color': '#fee8e9'});
		jss.set('.d2h-ins',{'background-color': '#dfd'});
		jss.set('.postbigb',{'background-color': 'orange'});
		jss.set('.putbigb',{'background-color': 'blue'});
		jss.set('.plusminuslist',{'background-color': 'orange'});
		jss.set('.panel-heading',{'border-bottom': '1px solid transparent'});
		jss.set('.modal-content',{'border': '0px black', 'background-color': '#ffffff', 'color': '#000000'});
		jss.set('.nav-tabs>li.active>a, .nav-tabs>li.active>a:hover, .nav-tabs>li.active>a:focus',{'border': '1px solid #ddd', 'background-color': '#ffffff', 'color': '#000000'});
		jss.set('.blockUI',{'border': '1px black #ddd', 'background-color': '#ffffff', 'color': '#000000'});
	}
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
        "multipartContent": {
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
            "enumVar": "miscMap.providers"
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
            "enumVar": "miscMap.hooks"
        },
        "postExecutionDataSourceHookName": {
            "type": "string",
            "defaultIndex": 0,
            "enumVar": "miscMap.hooks"
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

function addAndInit(ev, obj) {
	plusminuslistfunc(ev, obj);
	initEvents($('#ExampleBeanServiceImpl_form'));
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
			let obj = schema.enumVar;
			if(typeof obj == 'string') {
				obj = schema.enumVar.split('.').reduce((a, b) => a[b], window);
			}
            return decorateList(schema, obj, ovalut, isAttr + " " + labinpdet, nmdef, addclas + "" + dtcls, label, divstyle, width);
        } else {
            var sp1 = isAttr + " " + labinpdet;
            var sp2 = addclas + "" + dtcls;
            if (schema.hasOwnProperty('ui') && schema.ui == 'textarea') {
                return ("<div " + divstyle + " class=\"form-elems controls\">" + label + "<textarea mo-event=\"showInpTitle(this)\" " + sp1 + " " + nmdef + " class=\"form-control " + sp2 + "\" blur-event=\"validate(this, '" + schema.type + "')\" style=\"width:70%;height:200px;\">" + escapeHtml1(valut) + "</textarea></div>");

            } else {
                return ("<div " + divstyle + " class=\"form-elems controls\">" + label + "<input mo-event=\"showInpTitle(this)\" " + sp1 + " " + nmdef + " class=\"form-control " + sp2 + "\" blur-event=\"validate(this, '" + schema.type + "')\" value=\"" + escapeHtml1(valut) + "\" " + width + " type='text'/></div>");
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
            var sty = (schema.hasOwnProperty('label') && schema.label.border===true)?' style="border:1px dotted black;padding:10px;margin-top:10px;"':'';
            var html = "<div class=\"control-group\" "+sty+">" + slab;
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
            var html = '<div id="_element_' + hirNm + '" class="form-elems" style="border:1px dotted black;padding:10px;margin-top:10px;">' + slab + '<b>' + propLabel + '</b>&nbsp;&nbsp;<button type="button" class="plusminuslist" optype="true" list_value_cls="' + hirNm + '" click-event=\"addAndInit(event, this)\">Add</button>&nbsp;&nbsp;<button type="button" list_value_cls="' + hirNm + '" class="plusminuslist" optype="false" click-event=\"addAndInit(event, this)\">Remove</button><br/><br/>';
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
            var html = '<div id="_element_' + hirNm + '" class="form-elems" style="border:1px dotted black;padding:10px;margin-top:10px;">' + slab + '<b>' + propLabel + '</b>&nbsp;&nbsp;<button type="button" class="plusminuslist" optype="true" list_value_cls="' + hirNm + '" click-event=\"addAndInit(event, this)\">Add</button>&nbsp;&nbsp;<button type="button" list_value_cls="' + hirNm + '" class="plusminuslist" optype="false" click-event=\"addAndInit(event, this)\">Remove</button><br/><br/>';

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

function doLogin(e) {
	e.preventDefault();
	const params = $('#loginform').find('form').serializeObject();
	if(Object.keys(params).length!=2) return false;
	ajaxCall(true, "POST", "login", "application/x-www-form-urlencoded", "username="+params["username"]+"&password="+params["password"], {}, function(data) {
    	onSuccessLogin(data["token"]);
		ajaxCall(true, "GET", "misc", "", "", {}, function(data) {
	        miscMap = data;
	        startInitConfigTool(configuration);
	    }, null);
    }, function() {
    	alert("Invalid Authentication Credentials");
    });
    return false;
}

function onSuccessLogin(token) {
	auth_token = token;
	sessionStorage.setItem("token", token);
	$('#loginform').addClass('hidden');
	$('#toptoolbar').removeClass('hidden');
	$('#subnav').removeClass('hidden');
	$('#myModal').removeClass('hidden');
	$('#main').removeClass('hidden');
}

(function() {
    debugEnabled = false;
    serverUrl = "http://localhost:9080/";
    auth_token = "";

    $(document).ready(function() {
		$.ajaxPrefilter(function( options) {
			if(options.url.startsWith("configure") || options.url.startsWith("reports") || options.url.startsWith("misc") || options.url.startsWith("testcasefiles")
				 || options.url.startsWith("testcases") || options.url.startsWith("execute") || options.url.startsWith("profile")) {
				if(options.url.indexOf("?")==-1) options.url = options.url + "?token=" + auth_token;
				else options.url = options.url + "&token=" + auth_token;
			} else if(options.url.startsWith("/configure") || options.url.startsWith("/reports") || options.url.startsWith("/misc") || options.url.startsWith("/testcasefiles")
				 || options.url.startsWith("/testcases") || options.url.startsWith("/execute") || options.url.startsWith("/profile")) {
				if(options.url.indexOf("?")==-1) options.url = options.url + "?token=" + auth_token;
				else options.url = options.url + "&token=" + auth_token;
			}
		});
		
		$('#dmmode').click(darkMode);
		if(localStorage.getItem("theme")) {
			darkMode(localStorage.getItem("theme"));
		}
		
		if($('#loginform').is(':visible')) {
			if(sessionStorage.getItem("token")) {
				$('#loginform').addClass('hidden');
				auth_token = sessionStorage.getItem("token");
				ajaxCall(true, "GET", "misc", "", "", {}, function(data) {
		            miscMap = data;
		            onSuccessLogin(auth_token);
		            startInitConfigTool(configuration);
		        }, function(err, jq) {
		        	if(jq.status==401) {
		        		sessionStorage.removeItem("token");
		        		location.reload();
		        	}
		        });
		    }
		} else {
			ajaxCall(true, "GET", "misc", "", "", {}, function(data) {
	            miscMap = data;
	            startInitConfigTool(configuration);
	        }, null);
		}

		$('#srch-term').on('change', searchLeftNavs);
		$('.accordion-heading').on('click', function() {
			if($(this).find('a').attr('href')=="#") return;
			$($(this).find('a').attr('href')).toggleClass('in out');
			if($('#srch-term').val()!='') $('#srch-term').val('').trigger('change');
			$('#testcasefile-holder').find('.asideLink').css('background-color', currColor);
		});

		$(document).off('click').on('click', function(e) {
		    if(e.target && (e.target.tagName=='A' || e.target.tagName=='BUTTON') && $(e.target).attr('click-event')) {
		        var evt = $(e.target).attr('click-event');
				execFunction(evt, $(e.target));
		    }
		});
		
		$(document).off('contextmenu').on('contextmenu', function(e) {
		    if(e.target && (e.target.tagName=='A') && $(e.target).attr('contextmenu-event')) {
		        var evt = $(e.target).attr('contextmenu-event');
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
	$('.top_sel_but').hide();
	errdFilesReport = {};
	$('#editorTabs').html('').addClass('hidden');
    var htmm = '<a href="#" class="plusminuslist" click-event=\"seltestfailed = false;executionHandler(\'PUT\', true, \'' + pluginType + '\')\">Start Execution</a><br/></br/><a href="#" class="plusminuslist" click-event=\"executionHandler(\'DELETE\', true, \'' + pluginType + '\')\">Stop Execution</a><br/></br/><a href="#" class="plusminuslist" click-event=\"executionHandler(\'GET\', true, \'' + pluginType + '\')\">Check Execution Status</a><br/><br/><image id="image_status" src="resources/yellow.png"/>';
    if (pluginType.startsWith('executor')) {
        if (!calledbytestfpage) {
            execFiles = new Array();
        }
        calledbytestfpage = false;
        //if(!isSeleniumExecutor) {
        htmm += '<br/><br/><p>Overall Statistics</p><table id="lol_tbl"><tr><th>Tot Runs</th><th>Tot Tests</th><th>Failed Tests</th><th>Skip. Tests</th><th>Execution Time</th><th>Total Time</th></tr><tr><td id="lol_tr"></td><td id="lol_tt"></td><td id="lol_ft"></td><td id="lol_st"></td><td id="lol_eti"></td><td id="lol_tti"></td></tr></table><br><table id="lol_hdr"><thead><tr><th>Max</th><th>Min</th><th>Std Dev</th><th>Mean</th><th>50%</th><th>75%</th></tr></thead><tbody id="hdr1"></tbody><thead><tr><th>90%</th><th>97.5%</th><th>99%</th><th>99.9%</th><th>99.99%</th><th>99.999%</th></tr></thead><tbody id="hdr2"></tbody></table><!--div class="hidden"><br/>Subtest Statistics<br/><table class="table table-striped table-bordered table-hover" id="lol_sts" width="100%" style="width:100%;table-layout:fixed;word-wrap:break-word;color:black"><thead><tr><th style="color:black">Run No.&nbsp;&nbsp;</th><th style="color:black">Tot Tests</th><th style="color:black">Success Tests</th><th style="color:black">Fail. Tests</th></tr></thead><tbody></tbody></table></div--><br/><p>Run-Wise Statistics</p></table><table class="table table-striped table-bordered table-hover" id="lol_tblcu" width="100%" style="width:100%;table-layout:fixed;word-wrap:break-word;color:black"><thead><tr><th>Run No.&nbsp;&nbsp;</th><th>Tot Tests</th><th>Failed Tests</th><th>Skip. Tests</th><th>Time</th></tr></thead><tbody></tbody></table>';
        //}
        $('#ExampleBeanServiceImpl_form').html(htmm);
        if (pluginType=='executor-sel') {
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
	$('.top_sel_but').hide();
	errdFilesReport = {};
	$('#editorTabs').html('').addClass('hidden');
    var isExternal = configType == 'issuetrackerapi' ? true : false;
    ajaxCall(true, "GET", "testcases?testcaseFileName=" + extApiFileName + "&configType=" + configType, "", "", {}, function(isExternal, extApiFileName, configType) {
        return function(data1) {
        	$('#heading_main').html('Manage ' + (configType == 'issuetrackerapi'?'Issue Tracker APIs':'Server Logs APIs'));
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
                    $('#' + tcid).off('click.me').on('click.me', function(configType, extApiFileName, isExternal) {
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
var errdFilesReport;

function executionHandler(method, shwPp, pluginType) {
	if($('#lftpanel').find('.blockUI').length==0) $.blockUI({message:'<h4>Please Wait</h4>'}, $('#lftpanel'));
    var cdt = '';
    var cdttype = '';
    if (method == 'PUT' && pluginType.startsWith('executor')) {
		let type = pluginType.toLowerCase().endsWith(".sel")?".sel":".xml";
		if(execFiles[0].toLowerCase().endsWith(type)) {
			let efl = [];
			for(const ef of execFiles) {
				if(ef.toLowerCase().endsWith(type)) {
					efl.push(ef);
				}
			}
			execFiles = efl;
		}
        cdt = JSON.stringify(execFiles, undefined, 4);
        cdttype = 'application/json';
    }
    ajaxCall(false, method, "execute?pluginType=" + pluginType, cdttype, cdt, {}, function(shwPp, pluginType) {
        return function(data) {
			errdFilesReport = {};
            if (shwPp) alert(data.error);
            if (data.error == 'Execution already in progress..' || data.error == "Execution completed, check Reports Section") {
				if (data.error && data.error[2] && data.error[2].indexOf(".sel")!=-1) {
					if(data.others && data.others.length>0) {
						errdFilesReport[data.error[2]] = new Set();
						errdFilesReport[data.error[2]].push([data.error[1], data.error[3]]);
						//errdFilesReport.add([data.error[1], data.error[2]]);
						for(const v of data.others) {
							if(!errdFilesReport[v[2]]) errdFilesReport[v[2]] = new Set();
							errdFilesReport[v[2]].push([v[1], v[3]]);
							//errdFilesReport.add([v[1], v[2]]);
						}
						$('[click-event="getErroredSeleasyScripts()"]').eq(0).trigger('click');
						$.unblockUI({message:'<h4>Please Wait</h4>'}, $('#lftpanel'));
					} else {
						$('[tcfname="'+data.error[2]+'"]').trigger('click');
						setTimeout(function() {
							function makeMarker() {
								var marker = document.createElement("div");
								marker.style.color = "red";
								marker.innerHTML = "❌";
								return marker;
							}
							ceeditor.setGutterMarker(data["error"][1]-1, "breakpoints", makeMarker());
							$.unblockUI({message:'<h4>Please Wait</h4>'}, $('#lftpanel'));
						}, 2000);
					}
					return;
				}
                if (data.error == "Execution completed, check Reports Section") {
                    $("#image_status").attr("src", "resources/green.png");
                    if (loadTestData.hdr) {
                        $('#hdr1').html('<tr><td>' + loadTestData.hdr.maxValue + '</td><td>' + loadTestData.hdr.minNonZeroValue + '</td><td>' + loadTestData.hdr.stdDeviation.toFixed(2) + '</td><td>' + loadTestData.hdr.mean.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p50.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p75.toFixed(2) + '</td></tr/>');
                        $('#hdr2').html('<tr><td>' + loadTestData.hdr.summary.p90.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p97_5.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p99.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p99_9.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p99_99.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p99_999.toFixed(2) + '</td></tr/>');
                    }
                    $('#image_status').off().on('click', function() {
                        saveAsPngFile();
                    });
                    if(seltestfailed) $("#image_status").attr("src", "resources/red.png");
					$.unblockUI({message:'<h4>Please Wait</h4>'}, $('#lftpanel'));
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
                /*if (data.error == "Execution completed, check Reports Section") {
                    $("#image_status").attr("src", "resources/green.png");
                    if (loadTestData.hdr) {
                        $('#hdr1').html('<tr><td>' + loadTestData.hdr.maxValue + '</td><td>' + loadTestData.hdr.minNonZeroValue + '</td><td>' + loadTestData.hdr.stdDeviation.toFixed(2) + '</td><td>' + loadTestData.hdr.mean.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p50.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p75.toFixed(2) + '</td></tr/>');
                        $('#hdr2').html('<tr><td>' + loadTestData.hdr.summary.p90.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p97_5.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p99.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p99_9.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p99_99.toFixed(2) + '</td><td>' + loadTestData.hdr.summary.p99_999.toFixed(2) + '</td></tr/>');
                    }
                    $('#image_status').off().on('click', function() {
                        saveAsPngFile();
                    });
                    if(seltestfailed) $("#image_status").attr("src", "resources/red.png");
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
                }*/
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
			errdFilesReport = {};
            if (shwPp) alert(data.error);
            if (data.error && data.error[2] && data.error[2].indexOf(".sel")!=-1) {
				if(data.others && data.others.length>0) {
					errdFilesReport[data.error[2]] = new Set();
					errdFilesReport[data.error[2]].push([data.error[1], data.error[3]]);
					//errdFilesReport.add([data.error[1], data.error[2]]);
					for(const v of data.others) {
						if(!errdFilesReport[v[2]]) errdFilesReport[v[2]] = new Set();
						errdFilesReport[v[2]].push([v[1], v[3]]);
						//errdFilesReport.add([v[1], v[2]]);
					}
					$('[click-event="getErroredSeleasyScripts()"]').eq(0).trigger('click');
					$.unblockUI({message:'<h4>Please Wait</h4>'}, $('#lftpanel'));
				} else {
					$('[tcfname="'+data.error[2]+'"]').trigger('click');
					setTimeout(function() {
						function makeMarker() {
							var marker = document.createElement("div");
							marker.style.color = "red";
							marker.innerHTML = "❌";
							return marker;
						}
						ceeditor.setGutterMarker(data["error"][1]-1, "breakpoints", makeMarker());
						$.unblockUI({message:'<h4>Please Wait</h4>'}, $('#lftpanel'));
					}, 2000);
				}
				return;
			}
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
                    if(seltestfailed) $("#image_status").attr("src", "resources/red.png");
					$.unblockUI({message:'<h4>Please Wait</h4>'}, $('#lftpanel'));
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

var seltestfailed = false;
function loadStatisticsInTable2(data) {
    if (data && data.length > 0) {
		var isFailed = false;
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
                if(fail>0) isFailed = true;
            } else {
                var succ = dt[2] * 1 == 1 ? 1 : 0;
                var fail = dt[2] * 1 == 0 ? 1 : 0;
                if(fail>0) isFailed = true;
                $('#lol_sts').find('tbody').prepend(')<tr id="' + trid + '" data-succ="' + succ + '" data-fail="' + fail + '"><td style="color:black">' + dt[0] + '-' + dt[1] + '</td><td style="color:black">' + (succ + fail) + '</td><td style="color:black">' + succ + '</td><td style="color:black">' + fail + '</td></tr/>');
            }
        }
        seltestfailed = isFailed;
        if(isFailed) $("#image_status").attr("src", "resources/red.png");
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
    if (loadTestData.dyg) {
	    loadTestData.dyg.updateOptions({
	        'file': loadTestData.points
	    });
	}
    loadtestingdatatablecnt += ldata.length;
    $('#hdrstats').html();
    var len = ldata.length > 100 ? 100 : ldata.length;
    var isFailed = false;
    for (var i = 0; i < len; i++) {
        var data = ldata[ldata.length - i - 1];
        var url = loadtestingdatatablecnt - i;
        if (data[3] == null)
            url = 'L#' + url;
        else
            url = data[3] + '#' + url;
        if (data.url != null)
            url = '<a href="/reports/' + data[2] + '" click-event="openWind(this)">' + url + '</a>';
        else if(data[7]) {
			url = "<b>" + data[7] + "</b></br>" +  url;
		}
		if(data[5][1]*1>0) isFailed = true;
        htm += '<tr><td style="color:black">' + url + '</td><td style="color:black">' + data[5][0] + '</td><td style="color:black">' + data[5][1] + '</td><td style="color:black">' + data[5][2] + '</td><td style="color:black">' + data[5][3] + '</td></tr/>';
        currltsnum++;
    }
    seltestfailed = isFailed;
    if(isFailed) $("#image_status").attr("src", "resources/red.png");
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
    if (testfilen!=null && (testfilen.toLowerCase().endsWith(".sel") || testfilen.toLowerCase().endsWith(".xml"))) {
        execFiles = new Array();
        execFiles.push(testfilen);
	    calledbytestfpage = true;
	    let plugintype = testfilen.toLowerCase().endsWith(".sel")?'executor-sel':'executor-api';
	    executeHtml(plugintype);
	    executionHandler('PUT', true, plugintype);
	    execFiles = new Array();
    } else {
		alert("Please select valid test case files to execute");
	}
    seltestfailed = false;
    return false;
}

function execSelectedFiles(ele) {
	execFiles = new Array();
	execFilesT = [];
	let tests = $(ele).closest('form').find('table').find('tr');
	//let all = $('#select_all_tcs').is(":checked");
	for(const tr of tests) {
		if($(tr).find('td').eq(0).find('input').is(":checked") && $(tr).find('td>a.asideLink1').length>0) {
			const fnm = $(tr).find('td>a.asideLink1').next('input').val();
			if(fnm.toLowerCase().endsWith(".sel") || fnm.toLowerCase().endsWith(".xml")) {
				execFilesT.push([fnm, $(tr).find('.seqno').val()]);
			}
		}
	}
	execFilesT.sort(function(a, b) {
		return a[1] - b[1];
	});
	for(const ef of execFilesT) {
		execFiles.push(ef[0]);
	}
	if(execFiles.length>0) {
	    calledbytestfpage = true;
	    let plugintype = execFiles[0].toLowerCase().endsWith(".sel")?'executor-sel':'executor-api';
	    executeHtml(plugintype);
	    executionHandler('PUT', true, plugintype);
	    execFiles = new Array();
	} else {
		alert("Please select valid test case files to execute");
	}
	seltestfailed = false;
    return false;
}

function hideLeftPanel() {
	$('#lftpanel').hide();
	$('#rgtpanel').removeClass('col-md-9').addClass('col-md-12');
}

function showLeftPanel() {
	$('#lftpanel').show();
	$('#rgtpanel').removeClass('col-md-12').addClass('col-md-9');
}

function hideTopNav() {
	$('#hdtpi').hide();
	$('#sdtpi').show();
	$('#subnav').hide();
	$('#main').css('padding-top', '60px');
}

function showTopNav() {
	$('#hdtpi').show();
	$('#sdtpi').hide();
	$('#subnav').show();
	$('#main').css('padding-top', '120px');
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

function addTcFileHTml(foldername, tcfiles) {
	$('.top_sel_but').hide();
	errdFilesReport = {};
	$('#editorTabs').html('').addClass('hidden');
	if(!foldername) {
		$('#testcasefile-holder').find('.asideLink').css('background-color', currColor);
		$('#testcasefile-holder').find('a').eq(0).css('background-color', '#ddd');
	}
    var htmm = '<input type="text" placeholder="File Name" id="tcfile_name_holder_add">&nbsp;&nbsp;<textarea placeholder="Attibutes" id="tcfile_extras" rows="3" style="width: 300px;resize:vertical !important;margin-left: 10px;"></textarea><a style="margin-left: 10px;" href="#" class="plusminuslist" click-event=\"manageTcFileHandler(\'POST\', $(\'#tcfile_name_holder_add\').val(),\'\')\">Add Testcase File</a><br/><span><b style="font-size:11px;">Select All</b><input type="checkbox" id="select_all_tcs" style="margin-left: 7px;"></span></br/>';
    htmm += '<table border="1">';
    let opthtm = '<option value="10000000">-</option>';
    tcfiles = tcfiles?tcfiles:alltestcasefiles;
    for (var i = 0; i < tcfiles.length; i++) {
    	let extras = "";
    	if(tcfiles[i][1]) {
    		const lines = tcfiles[i][1].split("\n");
    		for(const l of lines) {
    			if(l.indexOf("|")!=-1 && (l.split("|")[1].trim().startsWith("https://") || l.split("|")[1].trim().startsWith("http://"))) {
    				extras += '<a target="_blank" href="'+l.split("|")[1].trim()+'">'+l.split("|")[0].trim()+'</a><br/>';
    			} else {
    				extras += '<p style="margin:0px">'+l.trim()+'</p>';
    			}
    		}
    	}
    	let es = "block";
    	if(extras) {
    		es = "none";
    	}
        var tcid = 'tcf_' + i;
        if(!foldername || (foldername && tcfiles[i][0].startsWith(foldername))) {
	        htmm += '<tr><td style="width:3%"><input type="checkbox" click-event="addRemoveExecFile(this,\'' + tcfiles[i][0] + '\')"></td>' + 
	       			'<td style="width:3%"><select class="seqno"></td>' + 
	        		'<td class="nmchng" style="width:44%;word-break:break-all;"><a href="#" id="' + tcid + '" class="asideLink1" click-event="triggerClick($(\'#tcfile_' + i + '\'))">' + tcfiles[i][0] + '</a><input id="inp_' + tcid + '" type="text" style="width:100%;display:none" value="' + tcfiles[i][0] + '" change-event="manageTcFileHandler(\'PUT\', $(\'#' + tcid + '\').html(), this.value)"/></td>' + 
	        		'<td style="width:42%"><div class="extctt">'+extras+'</div><textarea fid="'+tcid+'" class="editexcont" style="display:'+es+';width:100%;resize:vertical !important" rows="1">'+(tcfiles[i][1]?tcfiles[i][1]:'')+'</textarea>' +
	        		'<td style="width:8%"><center><table><tr><td style="text-align: center;border: 0px black;"><button type="button" click-event="manageTcFileHandler(\'DELETE\', $(\'#' + tcid + '\').html(),\'\')">Remove</button></td>' + 
	        		(tcfiles[i][0].endsWith('.props')?'':'<td style="text-align: center;border: 0px black;"><button type="button" click-event="execSelectedFileTests(\'' + tcfiles[i][0] + '\')">Execute</button></td>') + '</tr></table></center></td>'
	        		'</tr>';
    		opthtm += '<option value="'+(i+1)+'">'+(i+1)+'</option>';
    	}
    }
    htmm += '</table><br/><center><button type="button" click-event="execSelectedFiles(this)">Execute Selected</button></center>';
    $('#ExampleBeanServiceImpl_form').html(htmm);
    
    $('#ExampleBeanServiceImpl_form').find('select.seqno').append(opthtm);
    $('.extctt').dblclick(function() {
    	$(this).siblings('textarea').show();
    	$(this).hide();
    	const rl = $(this).siblings('textarea').val().split("\n").length;
    	$(this).siblings('textarea').attr('rows', rl);
    	$(this).siblings('textarea').focus();
    });
    $('.editexcont').change(function() {
    	manageTcFileHandler('PUT', $('#' + $(this).attr('fid')).text(), "", $(this).val());
    	$(this).siblings('div').show();
    });
    $('.editexcont').blur(function() {
    	if($(this).val()!="") {
	    	$(this).siblings('div').show();
	    	$(this).hide()
		}
    });
    $('.nmchng').dblclick(function() {
	    $(this).find('a').hide();
	    $('#inp_' + $(this).find('a').attr('id')).show();
	    $('#inp_' + $(this).find('a').attr('id')).focus();
    });
    $('.nmchng').find('input').blur(function() {
	    $(this).siblings('a').show();
	    $(this).hide();
    });
	initEvents($('#ExampleBeanServiceImpl_form'));
	
    $('#heading_main').html(`<span>Manage Tests</span><a style="margin-left: 10px;" href="#" class="plusminuslist" click-event="filterTestsByType(\'all\')">All</a>
    	<a style="margin-left: 10px;" href="#" class="plusminuslist" click-event="filterTestsByType(\'sel\')">sel</a>
    	<a style="margin-left: 10px;" href="#" class="plusminuslist" click-event="filterTestsByType(\'api\')">api</a>
    	<a style="margin-left: 10px;" href="#" class="plusminuslist" click-event="filterTestsByType(\'props\')">props</a>`);
}

function filterTestsByType(type) {
	/*let tcfiles;
	if(type=='sel') {
		tcfiles = [];
		for(const tgf of alltestcasefiles) {
			if(tgf[0].toLowerCase().endsWith(".sel")) tcfiles.push(tgf);
		}
	} else if(type=='api') {
		tcfiles = [];
		for(const tgf of alltestcasefiles) {
			if(tgf[0].toLowerCase().endsWith(".xml") || tgf[0].toLowerCase().endsWith(".csv") || tgf[0].toLowerCase().endsWith(".json")) tcfiles.push(tgf);
		}
	} else if(type=='props') {
		tcfiles = [];
		for(const tgf of alltestcasefiles) {
			if(tgf[0].toLowerCase().endsWith(".props")) tcfiles.push(tgf);
		}
	}
	addTcFileHTml(undefined, tcfiles);*/
	$('#ExampleBeanServiceImpl_form').children('table').children('tbody').children('tr').each(function() {
		const tgf = $(this).find('.asideLink1').text();
		if(type=='sel') {
			if(!tgf.toLowerCase().endsWith(".sel")) $(this).addClass('hidden');
			else $(this).removeClass('hidden');
		} else if(type=='api') {
			if(!tgf.toLowerCase().endsWith(".xml") && !tgf.toLowerCase().endsWith(".csv") && !tgf.toLowerCase().endsWith(".json")) $(this).addClass('hidden');
			else $(this).removeClass('hidden');
		} else if(type=='props') {
			if(!tgf.toLowerCase().endsWith(".props")) $(this).addClass('hidden');
			else $(this).removeClass('hidden');
		} else {
			$(this).removeClass('hidden');
		}
	});
}

function manageRenameFile(tcid) {
    $('#' + tcid).hide();
    $('#inp_' + tcid).show();
    return false;
}

function manageTcFileHandler(method, tcFileName, tcFileNameTo, extt) {
	let extras = "";
	if(method=='POST' && $('#tcfile_extras').val()) {
		extt = $('#tcfile_extras').val();
	}
	if(extt) extras = btoa(extt);
	if(!tcFileName) return false;
	if(method=='DELETE') {
		if(confirm("Are you Sure?") == false) {
			return false;
		}
	}
    ajaxCall(true, method, "testcasefiles?testcaseFileName=" + tcFileName + "&testcaseFileNameTo=" + tcFileNameTo + "&extras="+extras, "", "", {}, function(data) {
        if(data) alert(data);
        startInitConfigTool(addTcFileHTml);
    }, function(data) {
        if(data) alert(data);
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

var SELEASY=[
	["?", false, {displayText: "", text: " xpath@\"expr\"\n{\n\t//code when true\n}"}, {displayText: " - eval", text: " eval ${var}==\"Value\"\n{\n\t//code when true\n}"},
		  {displayText: " - browser", text: " browser-scope \"chrome\"\n{\n\t//code when true\n}"}, {displayText: " - session", text: " session-scope \"chrome\"\n{\n\t//code when true\n}"},
		  {displayText: " - relative", text: " relative tag@\"expr1\" above xpath@\"expr2\"\n{\n\t//code when true\n}"}, {displayText: " - selected", text: " xpath@\"expr\" selected\n{\n\t//code when true\n}"}],
	["?!", true, 0],
	"?? xpath@\"expr\"",
	"??- xpath@\"expr\"",
	"??+ xpath@\"expr\"",
	["if", true, 0],
	["ifnot", true, 0],
	[":", false, {displayText: "", text: "\n{\n\t//code when true\n}"}],//index = 7
	[":?", true, 0],
	[":?!", true, 0],
	["else if", true, 0],
	["else ifnot", true, 0],
	["else", true, 7],//index = 12
	["window", false, {displayText: " 0", text: " 0"}, {displayText: " main", text: " main"}, {displayText: " parent", text: " parent"}, {displayText: " block", text: " 1\n{\n\t//code to be executed\n}"}],
	["frame", true, 13],
	["tab", true, 13],
	"#",
	"##",
	"loop over",
	"#provider",
	"#counter",
	"#j",
	"#counter",
	"#sql",
	"#mongo",
	"#file",
	"#transient-variable",
	"#transient-provider",
	"break",
	"close",
	"continue",
	["eval", " ${var}==\"Value\""],
	"exec @print(\"Hello World\")",
	"goto \"https://abc.com\"",
	"import file.sel",
	["open", false, {displayText: " chrome", text: " chrome"}, {displayText: " chrome-hdl", text: " chrome-hdl"}, {displayText: " chrome-dkr", text: " chrome-dkr"},
			{displayText: " chrome-rec", text: " chrome-rec"}, {displayText: " firefox", text: " firefox"}, {displayText: " safari", text: " safari"},
			{displayText: " ie", text: " ie"}, {displayText: " opera", text: " opera"}, {displayText: " edge", text: " edge"},
			{displayText: " appium-android", text: " appium-android"}, {displayText: " appium-ios", text: " appium-ios"}],
	"require",
	"sleep 1000",
	["subtest", " \"subtestname\" (args)\n{\n\t//subtest logic\n}"],
	"@call \"subtestname\"",
	"@print()",
	"@driver",
	"@window",
	"@element",
	"@sc",
	"@printprovjson()",
	"@printProv()",
	"@index",
	"@line",
	"@cntxtparam()",
	"pass \"pass message\"",
	"fail \"failure message\"",
	"warn \"warning message\"",
	"dynprops file.props",
	"maximize",
	"include file.sel",
	"config file.props",
	"xpath@\"expr\"",
	"css@\"expr\"",
	"id@\"expr\"",
	"name@\"expr\"",
	"class@\"expr\"",
	"tag@\"expr\"",
	"cssselector@\"expr\"",
	"text@\"expr\"",
	"partiallinktext@\"expr\"",
	"linktext@\"expr\"",
	"jq@\"expr\"",
	"jquery@\"expr\"",
	"active",
	"this",
	"current",
	"var varname \"Some Value\"",
	["robot", false, {displayText: " keydown", text: " keydown 1"}, {displayText: " keyup", text: " keyup 1"}, {displayText: " keypress", text: " keypress 1"}],
	"type",
	"click",
	"back",
	"forward",
	"refresh",
	"clear",
	"submit",
	"setting",
	"jsvar",
	"execjs 'console.log(\"Hello\");'",
	"screenshot \"/path/to/file\"",
	"ele-screenshot xpath@\"expr\" \"/path/to/file\"",
	"hoverclick",
	"actions",
	"chord",
	"confirm",
	"alert",
	"moveby",
	"doubleclick",
	"dblclick",
	["netapix", false, {displayText: " on", text: " on GET http://abc.com/api/person"}, {displayText: " off", text: " off json var1@$.token,var2@$.firstname,$.mobileNo"}],
	["wopensave", false, {displayText: " on", text: " on"}, {displayText: " off", text: " off \"/path/to/file.pdf\" \"text\""}],
	"sendKeys",
	"moveto",
	"clickhold",
	"release",
	"keyup",
	"keydown",
	"api",
	"plugin",
	"jsonwrite",
	"xmlwrite",
	"zoom",
	"pinch",
	"tap",
	"swipe",
	"rotate",
	"hidekeypad",
	"shake",
	"chrome",
	"chrome-dkr",
	"chrome-hdl",
	"chrome-rec",
	"firefox",
	"opera",
	"safari",
	"ie",
	"edge",
	"appium-ios",
	"appium-android",
	"readfile",
	"mode",
	"hover",
	"upload '/path/to/file.txt' id@'ele1'",
	"randomize",
	"func \"funcname\" (args)",
	"relative",
	"sql",
	"dsq",
	"ds query",
	"file",
	"curl",
	"scroll",
	"visible",
	"attr",
	"fuzzyn",
	"fuzzya",
	"fuzzyauc",
	"fuzzyalc",
	"fuzzyan",
	"fuzzyanuc",
	"fuzzyanlc",
	"title",
	"selected",
	"above",
	"below",
	"leftof",
	"rightof",
	"near",
	"true",
	"false",
	"filevar",
	"others",
	"on",
	"off",
	"post",
	"get",
	"put",
	"delete",
	"tagname",
	"width",
	"height",
	"posx",
	"posy",
	"main",
	"cssvalue",
	"lazy",
	"jsonread",
	"jsonpath",
	"xmlread",
	"xmlpath",
	"mongo",
	"counter",
	"ds",
	"query",
	"timeout",
	"waitready",
	"scrollup",
	"scrollpageup",
	"scrollpagedown",
	"scrolldown",
	"execjsfile /path/to/file.js",
	"canvas xpath@\"expr\"",
	"touch",
	"layer",
	"normal",
	"integration",
	"parent",
	"clk_focus",
	"capability_set",
	"currenturl",
	"pagesource",
	"xpos",
	"ypos",
	"alerttext",
	"alphanumeric",
	"alpha",
	"alphanumericlc",
	"alphalc",
	"alphanumericuc",
	"alphauc",
	"numeric",
	"value",
	"range",
	"prefixed",
	"prefixed_",
	"status",
	"header",
	"json",
	"select",
	"clickandhold",
	"contextclick",
	"rightclick",
	"movetoelement",
	"draganddrop",
	"dragdrop",
	"dragdrop1",
	"draganddrop1",
	"movebyoffset",
	"ok",
	"yes",
	"cancel",
	"no",
	"enabled",
	"className"
];
var editorSynonyms = function(cm, option) {
    return new Promise(function(accept) {
      setTimeout(function() {
        var cursor = cm.getCursor(), line = cm.getLine(cursor.line);
        var start = cursor.ch, end = cursor.ch;
        while (start && /[^\s\t\n]/.test(line.charAt(start - 1))) --start;
        while (end < line.length && /[^\s\t\n]/.test(line.charAt(end))) ++end;
        var word = line.slice(start, end).toLowerCase();
        let matched = [];
        for (var i = 0; i < SELEASY.length; i++) {
        	if(typeof SELEASY[i]=='string' && SELEASY[i].startsWith(word)) {
        		matched.push(SELEASY[i]);
        	} else if(SELEASY[i][0].startsWith(word)) {
        		if(SELEASY[i].length==2) {
	        		for (var j = 1; j < SELEASY[i].length; j++) {
	        			let v = {text: SELEASY[i][0] + SELEASY[i][1], displayText: SELEASY[i][0]};
	        			matched.push(v);
	        		}
        		} else {
	        		const isAlias = SELEASY[i][1];
	        		const pointer = isAlias?SELEASY[SELEASY[i][2]]:SELEASY[i];
	        		for (var j = 2; j < pointer.length; j++) {
	        			let v = JSON.parse(JSON.stringify(pointer[j]));
	        			v.text = SELEASY[i][0] + v.text;
	        			v.displayText = SELEASY[i][0] + v.displayText;
	        			matched.push(v);
	        		}
	        	}
        	}
        }
        if(matched.length>0) {
	        if(matched[0]=="@call \"subtestname\"") {
				ajaxCall(true, "GET", 'testcasefiles?testcaseFileName='+currtestcasefile+'&allsubtests=true', "", "", {}, function(out) {
            		console.log(out);
					matched = out.length>0?out:["@call \"subtestname\""];
			        return accept({list: matched,
			             from: CodeMirror.Pos(cursor.line, start),
			             to: CodeMirror.Pos(cursor.line, end)});
        		}, null);
			} else {
		        return accept({list: matched,
		             from: CodeMirror.Pos(cursor.line, start),
		             to: CodeMirror.Pos(cursor.line, end)});
		    }
	    } else {
        	return accept(null);
        }
      }, 100)
    })
}

function loadTestCaseFileEditor() {
	//$('#req-txtarea').addClass('hidden');
	ceeditor = CodeMirror.fromTextArea(document.getElementById('req-txtarea'), {
		lineNumbers: true,
		lineWrapping: true,
		tabSize: 4,
		matchBrackets: true,
		styleActiveLine: true,
		extraKeys: {
			"Ctrl-Space": "autocomplete",
			"Cmd-Space": "autocomplete", 
			"Ctrl-B": function(cm) {cm.foldCode(cm.getCursor());},
			"Cmd-B": function(cm) {cm.foldCode(cm.getCursor());},
			"Ctrl-S": function(cm){execTc('post', onsucctcnmupdt, null);},
			"Cmd-S": function(cm){execTc('post', onsucctcnmupdt, null);},
			"Ctrl-R": function(cm){
				execTc('post', function() {
					onsucctcnmupdt();
					playTest(currtestcasefile, "", false, false);
				}, null);
			},
			"Cmd-R": function(cm){
				execTc('post', function() {
					onsucctcnmupdt();
					playTest(currtestcasefile, "", false, false);
				}, null);
			}
		},
		foldGutter: true,
		mode: 'text/x-seleasy',
		gutters: ["CodeMirror-linenumbers", "breakpoints", "CodeMirror-foldgutter"],
		viewportMargin: Infinity,
		theme: currTheme,
		autoCloseBrackets: true,
		hintOptions: {hint: editorSynonyms}
	});
	ceeditor.on('changes', function(cm) {
		const fedidi = sha256(currtestcasefile);
		if($('#'+fedidi).data('content')!=cm.getValue()) {
			$('#'+fedidi).data('content', cm.getValue());
			if($('#'+fedidi).find('.dirty').length==0) {
				$('#'+fedidi).append('<span class="dirty" style="position: absolute;top: 0px;left: 5px;font-size: 15px;color: #df5d1e;">*<span>');
			}
			if(celines!=ceeditor.lineCount()) {
				celines = ceeditor.lineCount();
				setTimeout(function() {
					editorEvents();
				}, 100);
			}
		} else if($('#'+fedidi).data('content')==cm.getValue() && $('#'+fedidi).find('.dirty').length>0) {
			$('#'+fedidi).find('.dirty').remove();
		}
	});
	if(celinedetails) {
		ceeditor.addLineClass(celinedetails-1, "wrap", "currentHighlight");
		ceeditor.scrollIntoView({line:celinedetails, char:0}, 200);
	}
	celines = ceeditor.lineCount();
	celinedetails = undefined;
	const fedidi = sha256(currtestcasefile);
	$('#'+fedidi).data('content', ceeditor.getValue());
	if(errdFilesReport && errdFilesReport[currtestcasefile]) {
		function makeMarker(errt) {
			var marker = document.createElement("div");
			marker.style.color = "red";
			marker.innerHTML = "<span class='error_mark_icon'>❌<span><b class='error_mark'></b>";
			$(marker).attr('title', errt);
			//marker.setAttribute('title', errt);
			return marker;
		}
		//currtestcasefile = fromErroredFile.error[2];
    	$('#93be7b20299b11e281c10800200c9a66_URL').val("testcases?testcaseFileName=" + currtestcasefile + "&configType=");
		$('#heading_main').html('Manage Tests >> ' + currtestcasefile);
		//ceeditor.setGutterMarker(fromErroredFile["error"][1]-1, "breakpoints", makeMarker(fromErroredFile.error[3]));
		for(const oter of errdFilesReport[currtestcasefile]) {
			ceeditor.setGutterMarker(oter[0]-1, "breakpoints", makeMarker(oter[1]));
		}
		//$('.error_mark_icon').attr('title', fromErroredFile.error[3]);
		showErrorAlert("Error executing seleasy script...Please resolve the errors and try again..");
		//window.scrollTo({top: $('.error_mark').offset().top-120, behavior: 'smooth'});
		//fromErroredFile = undefined;
	}
	if(currtestcasefile.toLowerCase().endsWith(".props")) {
		$('[id="play_test_case"]').addClass('hidden');
		$('[id="debug_test_case"]').addClass('hidden');
	} else {
		$('[id="play_test_case"]').removeClass('hidden');
		$('[id="debug_test_case"]').removeClass('hidden');
	}
	setTimeout(function() {
		if(openInDebugMode) {
			debugTest(currtestcasefile, "", false, false);
		} else {
			editorEvents();
		}
	}, 100);
}

var celinedetails, celines;
function editorEvents() {
	const lines = ceeditor.getValue().split("\n");
	$('.CodeMirror-code').find('pre').css('cursor', 'text');
	for(let i=0;i<lines.length;i++) {
		const line = lines[i];
		//const span = $('.CodeMirror-code').children().eq(i).find('pre').children().eq(0).find('span');
		if(line.trim().startsWith("dynprops ")) {
			$('.CodeMirror-code').children().eq(i).find('pre').css('cursor', 'pointer');
			$('.CodeMirror-code').children().eq(i).find('pre').off().dblclick(function() {
				ajaxCall(true, "GET", 'testcasefiles?testcaseFileName='+currtestcasefile+'&referencedFile='+line.trim().substring(9).trim(), "", "", {}, function(out) {
            		console.log(out);
            		if(out[0]) {
            			currtestcasefile = out[0];
						$('a[tcfname="'+(out[0])+'"]').trigger('click');
            		}
        		}, null);
			});
		} else if(line.trim().startsWith("config ")) {
			$('.CodeMirror-code').children().eq(i).find('pre').css('cursor', 'pointer');
			$('.CodeMirror-code').children().eq(i).find('pre').off().dblclick(function() {
				ajaxCall(true, "GET", 'testcasefiles?testcaseFileName='+currtestcasefile+'&referencedFile='+line.trim().substring(7).trim(), "", "", {}, function(out) {
            		console.log(out);
            		if(out[0]) {
            			currtestcasefile = out[0];
						$('a[tcfname="'+(out[0])+'"]').trigger('click');
            		}
        		}, null);
			});
		} else if(line.trim().startsWith("include ")) {
			$('.CodeMirror-code').children().eq(i).find('pre').css('cursor', 'pointer');
			$('.CodeMirror-code').children().eq(i).find('pre').off().dblclick(function() {
				ajaxCall(true, "GET", 'testcasefiles?testcaseFileName='+currtestcasefile+'&referencedFile='+line.trim().substring(8).trim(), "", "", {}, function(out) {
            		console.log(out);
            		if(out[0]) {
            			currtestcasefile = out[0];
						$('a[tcfname="'+(out[0])+'"]').trigger('click');
            		}
        		}, null);
			});
		} else if(line.trim().startsWith("import ")) {
			$('.CodeMirror-code').children().eq(i).find('pre').css('cursor', 'pointer');
			$('.CodeMirror-code').children().eq(i).find('pre').off().dblclick(function() {
				ajaxCall(true, "GET", 'testcasefiles?testcaseFileName='+currtestcasefile+'&referencedFile='+line.trim().substring(7).trim(), "", "", {}, function(out) {
            		console.log(out);
            		if(out[0]) {
            			currtestcasefile = out[0];
						$('a[tcfname="'+(out[0])+'"]').trigger('click');
            		}
        		}, null);
			});
		} else if(line.trim().startsWith("goto ")) {
			let url = line.trim().substring(5);
			let childrens = $('.CodeMirror-code').children().eq(i).find('pre').children().eq(0).find('span');
			for(let j=1;j<childrens.length;j++) {
				childrens.eq(j).remove();
			}
			$('.CodeMirror-code').children().eq(i).find('pre').children().eq(0).append('<span class="cm-string">'+url+'</span>');
			$('.CodeMirror-code').children().eq(i).find('pre').children().eq(0).css('cursor', 'pointer');
			$('.CodeMirror-code').children().eq(i).find('pre').off().dblclick(function() {
				if(url.startsWith("https://") || url.startsWith("http://"))
					window.open(url, '_blank');
				//else lookup from backend
			});
		} else if(line.trim().startsWith("@call ")) {
			let possibleSubtestFuncCall = line.trim();
			$('.CodeMirror-code').children().eq(i).find('pre').css('cursor', 'pointer');
			$('.CodeMirror-code').children().eq(i).find('pre').off().dblclick(function() {
				console.log(possibleSubtestFuncCall);
				ajaxCall(true, "GET", 'testcasefiles?testcaseFileName='+currtestcasefile+'&possibleSubtestFuncCall='+possibleSubtestFuncCall, "", "", {}, function(out) {
            		console.log(out);
					celinedetails = out[1];
					if(currtestcasefile == out[0]) {
						ceeditor.addLineClass(celinedetails, "wrap", "currentHighlight");
						ceeditor.scrollIntoView({line:celinedetails-1, char:0}, 200);
					}
					currtestcasefile = out[0];
					$('a[tcfname="'+(out[0])+'"]').trigger('click');
        		}, null);
				//lookup from backend and populate target file
			});
		} /*else if(span.length>0 && span.eq(0).hasClass("cm-string")) {
			let possibleSubtestFuncCall = line.trim();
			$('.CodeMirror-code').children().eq(i).find('pre').css('cursor', 'pointer');
			$('.CodeMirror-code').children().eq(i).find('pre').off().dblclick(function() {
				console.log(possibleSubtestFuncCall);
				ajaxCall(true, "GET", 'testcasefiles?testcaseFileName='+currtestcasefile+'&possibleSubtestFuncCall='+possibleSubtestFuncCall, "", "", {}, function(out) {
            		console.log(out);
					celinedetails = out[1];
					if(currtestcasefile == out[0]) {
						ceeditor.addLineClass(celinedetails, "wrap", "currentHighlight");
						ceeditor.scrollIntoView({line:celinedetails-1, char:0}, 200);
					}
					currtestcasefile = out[0];
					$('a[tcfname="'+(out[0])+'"]').trigger('click');
        		}, null);
				//lookup from backend and populate target file
			});
		}*/
	}
}

function onsavetestfile(data) {
	data = data.responseJSON;
	if(!data["error"]) {
		showErrorAlert(data);
		return;
	} else if(currtestcasefile!=data.error[2]) {
		$('a[tcfname="'+data.error[2]+'"]').trigger('click');
	} else {
		function makeMarker(errt) {
			var marker = document.createElement("div");
			marker.style.color = "red";
			marker.innerHTML = "<span class='error_mark_icon'>❌<span><b class='error_mark'></b>";
			$(marker).attr('title', errt);
			return marker;
		}
		currtestcasefile = data.error[2];
		$('#93be7b20299b11e281c10800200c9a66_URL').val("testcases?testcaseFileName=" + currtestcasefile + "&configType=");
		ceeditor.setGutterMarker(data["error"][1]-1, "breakpoints", makeMarker(data.error[3]));
		if(data["others"] && data["others"].length>0) {
			for(const oter of data.others) {
				ceeditor.setGutterMarker(oter[1]-1, "breakpoints", makeMarker(oter[3]));
			}
		}
		
		//$('.error_mark_icon').attr('title', data.error[3]);
		showErrorAlert("Error executing seleasy script...Please resolve the errors and try again..");
		//window.scrollTo({top: $('.error_mark').offset().top-120, behavior: 'smooth'});
	}
}

function startInitConfigTool(func) {
    ajaxCall(true, "GET", "testcasefiles", "", "", {}, function(func) {
        return function(data) {
            alltestcasefiles = [];
            $('#testcasefile-holder').html('');
            $('#testcasefile-holder').append('<a href="#" click-event="addTcFileHTml()" class="list-group-item asideLink">&nbsp;Manage Testcase Files</a>');

            var filesGrps = [];
            for (var t = 0; t < data.length; t++) {
                var testname = data[t][0];
                var folder = "";
                var fileName = testname;
                if (testname.indexOf("\\") != -1) {
                    folder = testname.substring(0, testname.lastIndexOf("\\"));
                    fileName = testname.substring(testname.lastIndexOf("\\") + 1);
                } else if(testname.indexOf("/") != -1) {
                    folder = testname.substring(0, testname.lastIndexOf("/"));
                    fileName = testname.substring(testname.lastIndexOf("/") + 1);
				}
                if (filesGrps[folder] == undefined) {
                    filesGrps[folder] = [];
                }
                filesGrps[folder].push({
                    fileName: fileName,
                    completeName: testname,
                    extra: data[t][1]
                });
            }

            filesGrps = Object.keys(filesGrps).sort().reduce(
				  (obj, key) => { 
				    obj[key] = filesGrps[key]; 
				    return obj;
				  }, 
				  {}
				);
            
            var tind = 0;
            for (var folder in filesGrps) {
                if (filesGrps.hasOwnProperty(folder)) {
                    filesGrps[folder].sort(function(a, b){return a.completeName.localeCompare(b.completeName)});
                    if (folder != "") {
                        var fid = 'folder_' + tind;
                        $('#testcasefile-holder').append('<a style="text-overflow: ellipsis;overflow: hidden;white-space: nowrap;" title="'+folder+'" status="hide" id="' + fid + '" href="#" class="list-group-item asideLink">&nbsp;<u>' + folder + '</u><button type="button" class="pull-right">Execute</button></a>');
                        $('#' + fid).attr('folder', folder);
                        $('#' + fid).off('click.me').on('click.me', function() {
							$('.top_sel_but').hide();
                            var escapedfolder = $(this).attr('folder').replace(/\\/g, '').replace(/\//g, '').replace(/-/g, '').replace(/\./g, '').replace(/\s+/g, '_');
                            if ($(this).attr('status') == "show") {
                                $('.' + escapedfolder + '_claz').hide();
                                $(this).attr('status', 'hide');
                                $('.' + escapedfolder + '_claz[folder]').attr('status', 'show');
                                $('.' + escapedfolder + '_claz[folder]').trigger('click');
                                addTcFileHTml();
                            } else {
								$('#testcasefile-holder').find('.asideLink').css('background-color', currColor);
								$(this).css('background-color', '#ddd');
                            	$('.' + escapedfolder + '_claz').show();
                                $(this).attr('status', 'show');
                                addTcFileHTml($(this).attr('folder'));
                                //$('.' + escapedfolder + '_claz[folder]').attr('status', 'show');
                                //$('.' + escapedfolder + '_claz[folder]').trigger('click');
                            }
                            return false;
                        });
                        $('#' + fid).find('button').off('click.me').on('click.me', function() {
							var escapedfolder = $(this).parent().attr('folder').replace(/\\/g, '').replace(/\//g, '').replace(/-/g, '').replace(/\./g, '').replace(/\s+/g, '_');
							execFiles = new Array();
							$('.' + escapedfolder + '_claz').each(function() {
								execFiles.push($(this).attr('tcfname'));
							});
							calledbytestfpage = true;
							let plugintype = execFiles[0].toLowerCase().endsWith(".sel")?'executor-sel':'executor-api';
						    executeHtml(plugintype);
						    executionHandler('PUT', true, plugintype);
						    execFiles = new Array();
						});
                    }

                    for (var t = 0; t < filesGrps[folder].length; t++, tind++) {
						alltestcasefiles.push([filesGrps[folder][t].completeName, filesGrps[folder][t].extra]);
                        var testFileName = filesGrps[folder][t].completeName;
                        var fileName = filesGrps[folder][t].fileName;
                        var id = 'tcfile_' + tind;
                        if (firstFile == '')
                            firstFile = id;
                        if (folder != "") {
                            var escapedfolder = folder.replace(/\\/g, '').replace(/\//g, '').replace(/-/g, '').replace(/\./g, '').replace(/\s+/g, '_');
                            $('#testcasefile-holder').append('<a style="margin-left:20px;display:none" id="' + id + '" href="#" class="list-group-item asideLink ' + escapedfolder + '_claz">↳&nbsp;' + fileName + '</a>');
                        } else {
                            $('#testcasefile-holder').append('<a id="' + id + '" href="#" class="list-group-item asideLink">&nbsp;' + testFileName + '</a>');
                        }
                        let iconn = fileName.endsWith(".props")?"properties.jpg":"testicon.png"; 
                        $('#' + id).prepend('<img style="position:absolute;right:5px;top:1px;width:15px" src="images/'+iconn+'"/>');
                        $('#' + id).attr('tcfname', testFileName);
                        $('#' + id).off('contextmenu').on('contextmenu', function(e) {
							e.preventDefault();
							$(this).css('background-color', '#ddd');
							if($('#testcasefile-holder').data('files')) {
								$('#testcasefile-holder').data('files').push($(this).attr('tcfname'));
								if($('#testcasefile-holder').data('files').length==2) {
									var f1 = $('#testcasefile-holder').data('files')[0], f2 = $('#testcasefile-holder').data('files')[1];
									$.get("testcases?testcaseFileName=" + f1, function(lhs) {
										$.get("testcases?testcaseFileName=" + f2, function(rhs) {
											var diff = Diff.createTwoFilesPatch(f1, f2, lhs, rhs);
											var diffHtml = Diff2Html.html(diff, {
												drawFileList: false,
											    matching: 'lines',
											    outputFormat: 'side-by-side',
											});
											$('#heading_main').html('Compare Files<button style="font-size: 15px;" type="button" class="pull-right">Clear</button>');
											$('#ExampleBeanServiceImpl_form').html(diffHtml);
											$('#heading_main').find('button').off('click.me').on('click.me', function() {
												$('#testcasefile-holder').data('files', []);
												$('#testcasefile-holder').find('.asideLink').css('background-color', currColor);
												addTcFileHTml();
											});
										});
									});
									$('#testcasefile-holder').data('files', []);
								}
							} else {
								$('#testcasefile-holder').data('files', [$(this).attr('tcfname')]);
							}
						});
                        $('#' + id).off('click.me').on('click.me', function() {
							$('#testcasefile-holder').find('.asideLink').css('background-color', currColor);
							$(this).css('background-color', '#ddd');
							$(this).css('color', currColor=='#000000'?'white':'black');
							//$('#srch-term').val($(this).text().trim()).trigger('change');
                            currtestcasefile = $(this).attr('tcfname');
                            $('#93be7b20299b11e281c10800200c9a66_URL').val("testcases?testcaseFileName=" + currtestcasefile + "&configType=");
                            $('#heading_main').html('Manage Tests >> ' + currtestcasefile);
                            currtestcases = [''];
                            ajaxCall(true, "GET", "testcases?testcaseFileName=" + currtestcasefile, "", "", {}, function(data1) {
                                var htmm = '<button type="button" class="plusminuslist" click-event=\"addTestCase(true, null, \'\', null, false, false)\">Add New Testcase</button><br/></br/>';
                                if (currtestcasefile.toLowerCase().endsWith(".sel") || currtestcasefile.toLowerCase().endsWith(".props") || currtestcasefile.toLowerCase().endsWith(".csv")) {
                                    htmm = "";
                                    if(ceeditor) {
                                    	if($('#req-txtarea').length>0) {
                                    		try {
                                    			ceeditor.toTextArea();
                                    		} catch(er) {}
                                    	}
                                    	else {
                                    		ceeditor = undefined;
                                    	}
                                    }
                                    $('#ExampleBeanServiceImpl_form').html('<textarea class="hidden" id="req-txtarea" rows=100 style="width:90%">' + data1 + '</textarea>');
                                    prepareForm("testcases?testcaseFileName=" + currtestcasefile + "&configType=", "POST", "💾", "onsucctcnmupdt", null, true, "sel_test_case");
                                    initEvents($('#ExampleBeanServiceImpl_form'));
                                    
									$('.org_save_butt').remove();
									bthm = '<button type="button" style="position: absolute;left: 20px;top: 5px;" click-event="gotoBottom()">↓</button> \
												<button type="button" style="position: absolute;left: 20px;bottom: 25px;" click-event="gotoTop()">↑</button>';
									$('#float_action_bar').append('<button id="save_test_case" type="button" style="display:none;position:absolute;top:13px;right:200px;" class="top_sel_but post" click-event="execTc(\'post\', onsucctcnmupdt, onsavetestfile)">💾</button>');
									$('.top_sel_but').show();
									
									//$('#buttons_cont').append('<button type="button" style="position: absolute;right: 105px;top: 5px;" id="play_test_case" type="submit" class="" type="submit">▶</button><button type="button" style="position: absolute;right: 70px;top: 5px;" id="debug_test_case" type="submit" class="" type="submit">| |</button>');
									//$('#buttons_cont').append('<button type="button" id="debug_test_case" style="position:absolute;right:70px;bottom:25px;" type="submit" class="" type="submit">| |</button><button type="button" style="position:absolute;right:105px;bottom:25px;" id="play_test_case" type="submit" class=" pull-right" type="submit">▶</button>');
                                    $('#ExampleBeanServiceImpl_form').append('<div id="play_result_area"></div><div id="debug_result_area"></div>');
                                    $('[id="play_test_case"]').off('click.me').on('click.me', function() {
                                        playTest(currtestcasefile, "", false, false);
                                        return false;
                                    });
                                    $('[id="debug_test_case"]').off('click.me').on('click.me', function() {
                                        debugTest(currtestcasefile, "", false, false);
                                        return false;
                                    });
                                    const fedid = sha256(currtestcasefile);
                                    $("#editorTabs").removeClass('hidden');
                                    if($('#'+fedid).length==0) {
                                    	$("#editorTabs").find('li').removeClass('active');
                                    	const fld = currtestcasefile.length>15?(currtestcasefile.substring(0,15)+"..."):currtestcasefile;
                                    	$("#editorTabs").append('<li id="'+fedid+'" class="active"><a href="#" id="'+fedid+'">'+fld+'<span style="padding-left:10px;font-size:8px;cursor:pointer;" class="btn_close">❌<span></a></li>');
                                    	$('#'+fedid).attr('title', currtestcasefile);
                                    	$('#'+fedid).find('.btn_close').off('click.me').on('click.me', function(event) {
                                    		event.stopPropagation();
                                    		const fli = $(this).parent().parent().parent().children('li');
                                    		const currpos = $(this).parent().parent().index();
                                    		const thsele = $(this);
                                    		if($(this).parent().parent().find('.dirty').length>0) {
	                                    		bootbox.confirm({
	                                    			animate: false,
					                                message: 'You have pending unsaved unchanges, Do you want to close the file?',
					                                buttons: {
						                                confirm: {
							                                label: 'Yes',
							                                className: 'btn-success'
						                                },
						                                cancel: {
							                                label: 'No',
							                                className: 'btn-danger'
						                                }
					                                },
					                                callback: function (result) {
					                                	if(result) {
															if(currpos>0) {
				                                    			fli.eq(currpos-1).addClass('active');
				                                    			fli.eq(currpos-1).trigger('click');
				                                    		} else {
				                                    			if(fli.length>1) {
				                                    				fli.eq(currpos+1).addClass('active');
				                                    				fli.eq(currpos+1).trigger('click');
				                                    			} else {
				                                    				addTcFileHTml();
				                                    			}
				                                    		}
				                                    		thsele.parent().parent().remove();					                                	
					                                	}
					                                }
						                        });
						                	} else {
						                		if(currpos>0) {
	                                    			fli.eq(currpos-1).addClass('active');
	                                    			fli.eq(currpos-1).trigger('click');
	                                    		} else {
	                                    			if(fli.length>1) {
	                                    				fli.eq(currpos+1).addClass('active');
	                                    				fli.eq(currpos+1).trigger('click');
	                                    			} else {
	                                    				addTcFileHTml();
	                                    			}
	                                    		}
	                                    		thsele.parent().parent().remove();
						                	}
                                    	});
                                    	$('#'+fedid).off('click.me').on('click.me', function() {
                                    		if($('.blockUI').length==0) $.blockUI({message: '<h3><img src="resources/busy.gif" /> Just a moment...</h3>'});
                                    		currtestcasefile = $(this).attr('title');
		                                	const fedidi = sha256(currtestcasefile);
		                                	$("#editorTabs").find('li').removeClass('active');
                                    		$('#'+fedidi).addClass('active');
                                    		$('#93be7b20299b11e281c10800200c9a66_URL').val("testcases?testcaseFileName=" + currtestcasefile + "&configType=");
                                    		$('#heading_main').html('Manage Tests >> ' + currtestcasefile);
                                    		if($(this).find('.dirty').length>0) {
	                                    		bootbox.confirm({
	                                    			animate: false,
					                                message: 'You have pending unsaved unchanges, Do you want to ignore the changes and load the current file content instead?',
					                                buttons: {
						                                confirm: {
							                                label: 'Yes',
							                                className: 'btn-success'
						                                },
						                                cancel: {
							                                label: 'No',
							                                className: 'btn-danger'
						                                }
					                                },
					                                callback: function (cfile) {
														return function(result) {
															const fed_id = sha256(cfile);
						                                	if(!result) {
						                                		if(ceeditor) ceeditor.toTextArea();
						                                		$('#req-txtarea').val($('#'+fed_id).data('content'));
							                                    loadTestCaseFileEditor();
							                                    $.unblockUI();
						                                	} else {
						                                		$('#'+fed_id).find('.dirty').remove();
						                                		//$('#req-txtarea').addClass('hidden');
						                                		ajaxCall(true, "GET", "testcases?testcaseFileName=" + cfile, "", "", {}, function(content) {
						                                			if(ceeditor) ceeditor.toTextArea();
						                                    		$('#req-txtarea').val(content);
								                                    loadTestCaseFileEditor();
					                                    		}, null);
						                                	}
						                                };
					                                }(currtestcasefile)
					                        	});
	                                    	} else {
	                                    		//$('#req-txtarea').addClass('hidden');
	                                    		ajaxCall(true, "GET", "testcases?testcaseFileName=" + currtestcasefile, "", "", {}, function(content) {
	                                    			if(ceeditor) ceeditor.toTextArea();
		                                    		$('#req-txtarea').val(content);
				                                    loadTestCaseFileEditor();
	                                    		}, null);
	                                    	}
                                    	});
                                    	loadTestCaseFileEditor();
                                    } else {
                                   		$("#editorTabs").find('li').removeClass('active');
                                   		$('#'+fedid).addClass('active');
                                    	if($('#'+fedid).find('.dirty').length>0) {
                                    		bootbox.confirm({
	                                    		animate: false,
				                                message: 'You have pending unsaved unchanges, Do you want to ignore the changes and load the current file content instead?',
				                                buttons: {
					                                confirm: {
						                                label: 'Yes',
						                                className: 'btn-success'
					                                },
					                                cancel: {
						                                label: 'No',
						                                className: 'btn-danger'
					                                }
				                                },
				                                callback: function (cfile) {
													return function(result) {
														const fed_id = sha256(cfile);
					                                	if(!result) {
					                                		$('#req-txtarea').val($('#'+fed_id).data('content'));
					                                	} else {
					                                		$('#'+fed_id).find('.dirty').remove();
					                                	}
					                                	loadTestCaseFileEditor();
					                                	$.unblockUI();
					                                };
				                                }(currtestcasefile)
				                        	});
                                    	} else {
                                			loadTestCaseFileEditor();
                                			$.unblockUI();
                                		}
                                	}
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
                                        $('#' + tcid).off('click.me').on('click.me', function() {
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
            
            /*let result = [];
			let level = {result};
			Object.keys(filesGrps).forEach(path => {
			  path.split(/\/|\\/).reduce((r, name, i, a) => {
			    if(!r[name]) {
			      r[name] = {result: []};
			      r.result.push({name, children: r[name].result})
			    }
			    return r[name];
			  }, level)
			});
			console.log(result);*/
			
			$('.asideLink[folder*="/"]').each(function() {
				$(this).html("↓ " + $(this).html());
				$(this).hide();
				const prts = $(this).attr('folder').split(/\/|\\/);
				prts.splice(-1);
				const sep = $(this).attr('folder').split('/').length==prts.length?'/':'\\';
				const ef = prts.join(sep).replace(/\\/g, '').replace(/\//g, '').replace(/-/g, '').replace(/\./g, '').replace(/\s+/g, '_');
				$(this).addClass(ef+'_claz');
				/*let pp = [];
				for(let i=0;i<prts.length-1;i++) {
					pp.push(prts[i]);
					const lbo = pp.join(sep);
					const efc = lbo.replace(/\\/g, '').replace(/\//g, '').replace(/-/g, '').replace(/\./g, '').replace(/\s+/g, '_');
					$(this).addClass(efc+'_claz');
					$('.'+ef+'_claz').addClass(efc+'_claz');
				}*/
			});
            darkMode(localStorage.getItem("theme"));
            if (typeof func == "function") func();
        };
    }(func), null);
}

function showSuccessAlert(msg) {
	bootbox.alert({
	    animate: false,
        message: msg,
        backdrop: true
    });
}

function showErrorAlert(msg) {
	bootbox.alert({
	    animate: false,
        message: msg,
        className: 'rubberBand errored',
        callback: function () {
        	if($('.error_mark').length>0)
        		window.scrollTo({top: $('.error_mark').offset().top-120, behavior: 'smooth'});
        }
    });
}

function onsucctcnmupdt() {
	$('.error_mark_icon').parent().remove();
    var tc = $('input[name="name"]').val();
    var ac = $('#ExampleBeanServiceImpl_form').attr("action");
    ac = ac.substring(0, ac.lastIndexOf("=") + 1) + tc;
    $('#ExampleBeanServiceImpl_form').attr("action", ac);
    $('#93be7b20299b11e281c10800200c9a66_URL').val(ac);
    if(errdFilesReport && Object.keys(errdFilesReport).length>0 && ceeditor) {
		for(const v of Object.keys(errdFilesReport)) {
			if($('#heading_main').text().endsWith(v)) {
				for(const vo of errdFilesReport[v]) {
					ceeditor.setGutterMarker(vo[0]-1, "breakpoints", null);
				}
			}
		}
		delete errdFilesReport[currtestcasefile];
	}
	if($('#heading_main').text().startsWith("Manage Tests")) {
		if(currtestcasefile.toLowerCase().endsWith(".sel") || currtestcasefile.toLowerCase().endsWith(".props") || currtestcasefile.toLowerCase().endsWith(".csv")) {
			const fedidi = sha256(currtestcasefile);
			$('#'+fedidi).find('.dirty').remove();
		} else {
			showSuccessAlert("Test Script saved Successfully...");
		}
	}
	else if($('#heading_main').text().startsWith("Manage Server Logs")) showSuccessAlert("Server Logs API saved Successfully...");
	else if($('#heading_main').text().startsWith("Manage Issue Tracker")) showSuccessAlert("Issue Tracker API saved Successfully...");
	//window.scrollTo({top: $('#buttons_cont').offset().top-120, behavior: 'smooth'});
	//$("html, body").animate({ scrollTop: $(document).height() }, 1000);
}

function addTestCase(isNew, data, configType, tcfname, isServerLogsApi, isExternalLogsApi) {
    countMap = {};
    if (tcfname == null)
        tcfname = currtestcasefile;
    if (isNew) {
        document.getElementById('ExampleBeanServiceImpl_form').innerHTML = generateFromValue(schema, '', true, '', '', data, false, true, true, '');
        prepareForm('testcases?testcaseFileName=' + tcfname + '&configType=' + configType, 'POST', '💾', "onsucctcnmupdt", null);
		initEvents($('#ExampleBeanServiceImpl_form'));
    } else {
        document.getElementById('ExampleBeanServiceImpl_form').innerHTML = generateFromValue(schema, '', true, '', '', data, false, true, true, '');
        prepareForm('testcases?testcaseFileName=' + tcfname + '&configType=' + configType + '&tcName=' + data["name"], 'PUT', '💾', "onsucctcnmupdt", null);
        initEvents($('#ExampleBeanServiceImpl_form'));
        if (tcfname != null && data != null) {
            $('#ExampleBeanServiceImpl_form').append('<button type="button"style="position: absolute;right: 100px;top: 55px;" id="play_test_case" type="submit" class="postbigb" type="submit">Test</button><button type="button"style="position: absolute;right: 100px;bottom: 135px;" id="play_test_case" type="submit" class="postbigb" type="submit">Test</button><br/><div id="play_result_area"></div>');
            $('[id="play_test_case"]').off('click.me').on('click.me', function(tcfname, name, isServerLogsApi, isExternalLogsApi) {
                return function() {
                    playTest(tcfname, name, isServerLogsApi, isExternalLogsApi);
                    return false;
                }
            } (tcfname, data["name"], isServerLogsApi, isExternalLogsApi));
            if (tcfname.toLowerCase().endsWith(".sel"))  {
            	$('#ExampleBeanServiceImpl_form').append('<button type="button" id="debug_test_case" type="submit" class="postbigb" type="submit">Debug</button><br/><div id="debug_result_area"></div>');
	            $('#debug_test_case').off('click.me').on('click.me', function(tcfname, name, isServerLogsApi, isExternalLogsApi) {
	                return function() {
	                    debugTest(tcfname, name, isServerLogsApi, isExternalLogsApi);
	                    return false;
	                }
	            } (tcfname, data["name"], isServerLogsApi, isExternalLogsApi));
	        }
        }
    }
}

//var fromErroredFile;
function playTest(tcf, tc, isServerLogsApi, isExternalLogsApi) {
	//fromErroredFile = undefined;
    var isserverlogfile = isServerLogsApi ? "&isServerLogsApi=true" : "";
    isserverlogfile += isExternalLogsApi ? "&isExternalLogsApi=true" : "";
    ajaxCall(true, "PUT", "/reports?action=playTest&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
        return function(data) {
            if (tcf.toLowerCase().endsWith(".sel")) {
				errdFilesReport = {};
                $("#myModalB").html('Test Report for script ' + tcf);
                $("#myModalB").html('<iframe src="/reports/'+Object.values(data.paths[0])[0][0].left+'/selenium-index.html" style="width:100%;height:500px;border:none;"></iframe>');
                $("#myModal").modal();
                return;
            }
            var content = getTestResultContent1(data);
            $('#play_result_area').html(content);
            if (!tcf.toLowerCase().endsWith(".sel")) {
            	$('#api_execution_reponse').show();
            	setTimeout(function() {
            		window.scrollTo({top: $('#api_execution_reponse').offset().top, behavior: 'smooth'});
            	}, 500);
            } else {
            	$('#api_execution_reponse').hide();
            	showSuccessAlert("Script Execution Successfull...");
            }
            //$("html, body").animate({ scrollTop: $(document).height() }, 1000);
        };
    }(tcf), function(tcf){
		return function(data) {
            if (data && tcf.toLowerCase().endsWith(".sel")) {
				if(data["error"]) {
					errdFilesReport = {};
					errdFilesReport[data.error[2]] = new Set();
					errdFilesReport[data.error[2]].add([data.error[1], data.error[3]]);
					if(data["others"] && data["others"].length>0) {
						for(const oter of data.others) {
							if(!errdFilesReport[oter[2]]) errdFilesReport[oter[2]] = new Set();
							errdFilesReport[oter[2]].add([oter[1], oter[3]]);
						}
					}
				}
				if(!data["error"]) {
					showErrorAlert(data);
					return;
				} else if(tcf!=data.error[2]) {
            		$('a[tcfname="'+data.error[2]+'"]').trigger('click');
            	} else {
					function makeMarker(errt) {
						var marker = document.createElement("div");
						marker.style.color = "red";
						marker.innerHTML = "<span class='error_mark_icon'>❌<span><b class='error_mark'></b>";
						$(marker).attr('title', errt);
						return marker;
					}
					currtestcasefile = data.error[2];
					$('#93be7b20299b11e281c10800200c9a66_URL').val("testcases?testcaseFileName=" + currtestcasefile + "&configType=");
					ceeditor.setGutterMarker(data["error"][1]-1, "breakpoints", makeMarker(data.error[3]));
					if(data["others"] && data["others"].length>0) {
						for(const oter of data.others) {
							ceeditor.setGutterMarker(oter[1]-1, "breakpoints", makeMarker(oter[3]));
						}
					}
					
					//$('.error_mark_icon').attr('title', data.error[3]);
					showErrorAlert("Error executing seleasy script...Please resolve the errors and try again..");
					//window.scrollTo({top: $('.error_mark').offset().top-120, behavior: 'smooth'});
				}
				let path = Object.values(data.paths[0])[0][0].left;
				$.get('/reports/'+path+'/selenium-index.html', function() {
					$("#myModalB").html('Test Report for script ' + tcf);
	                $("#myModalB").html('<iframe src="/reports/'+path+'/selenium-index.html" style="width:100%;height:500px;border:none;"></iframe>');
	                $("#myModal").modal();
				});
			} else if(data) {
				showErrorAlert(data);
			} else {
				showErrorAlert("Unknown Error Occurred");
			}
		};
	}(tcf));
}

const uid = function() {
    return Date.now().toString(36) + Math.random().toString(36).substr(2);
}
function makeDebugMarker() {
	var marker = document.createElement("div");
	marker.style.color = "#822";
	marker.innerHTML = "●";
	return marker;
}

var ceeditor, prevline, chkIntv, sessionId, cstate, openInDebugMode;
function debugTest(tcf, tc, isServerLogsApi, isExternalLogsApi) {
	cstate = undefined;
	var dbgctrl = $('#req-txtarea').width() + $('#req-txtarea').offset().left - 100;
	if(!sessionId) sessionId = uid();
	var isserverlogfile = "&sessionId="+sessionId;
	$('#req-txtarea').data('tcf', tcf);
	ceeditor.toTextArea();
	ceeditor = CodeMirror.fromTextArea(document.getElementById('req-txtarea'), {
		lineNumbers: true,
		lineWrapping: true,
		tabSize: 4,
		matchBrackets: true,
		styleActiveLine: true,
		//readOnly: 'nocursor',
		//extraKeys: {"Ctrl-B": function(cm){ cm.foldCode(cm.getCursor()); }},
    	foldGutter: true,
		mode: 'text/x-seleasy',
		gutters: ["CodeMirror-linenumbers", "breakpoints", "CodeMirror-foldgutter"],
		viewportMargin: Infinity,
		theme: currTheme,
		autoCloseBrackets: true
    	//hintOptions: {hint: editorSynonyms}
	});
	if(openInDebugMode) {
		$.blockUI({ message: '<h3><img src="resources/busy.gif" /> Debugger Stepping...</h3>' }, $('.CodeMirror'));
		ceeditor.removeLineClass(prevline, 'background', 'CodeMirror-activeline-background');
		ceeditor.setGutterMarker(prevline, "breakpoints", null);
		prevline = openInDebugMode["p"]*1 - 1;
		ceeditor.setGutterMarker(prevline, "breakpoints", makeDebugMarker());
		ceeditor.addLineClass(prevline, 'background', 'CodeMirror-activeline-background');
		$.unblockUI(undefined, $('.CodeMirror'));
		openInDebugMode = undefined;
	}
	ceeditor.on("gutterClick", function(cm, n) {
		var info = cm.lineInfo(n);
		var dal = $('#debug-controls').data("dal");
		let flg = false;
		for(let i=0;i<dal[currtestcasefile].length;i++) {
			for(let j=0;j<dal[currtestcasefile][i].length;j++) {
				if(dal[currtestcasefile][i][j][0]==(n+1)) {
					flg = true;
					break;
				}
			}
		}
		if(!flg) {
			showErrorAlert("Invalid debugger line..");
			return;
		}
		if(info.gutterMarkers) {
			ajaxCall(true, "PUT", "/reports?action=debug&line=r"+n+"&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
		        return function(data) {
		        	if(data["s"]===false) {
		        		alert(data["m"]);
		        	} else {
		        		ceeditor.setGutterMarker(n, "breakpoints", info.gutterMarkers ? null : makeDebugMarker());
		        	}
		        };
		    }(tcf), null);
		} else {
			ajaxCall(true, "PUT", "/reports?action=debug&line=b"+n+"&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
		        return function(data) {
		        	if(data["s"]===false) {
		        		alert(data["m"]);
		        	} else {
		        		ceeditor.setGutterMarker(n, "breakpoints", info.gutterMarkers ? null : makeDebugMarker());
		        	}
		        };
		    }(tcf), null);
		}
	});
	const stop = function(cm) {
		ajaxCall(true, "PUT", "/reports?action=debug&line=-5&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
	        return function(data) {
	        	if(data["s"]===false) {
	        		alert(data);
	        	} else {
	        		cstate = -5;
	        		$.blockUI({ message: '<h3><img src="resources/busy.gif" /> Debugger Suspending...</h3>' }, $('.CodeMirror'));
	        		line = data["n"]*1 - 1;
	        		if(prevline!=data["p"]-1) {
		        		ceeditor.removeLineClass(prevline, 'background', 'CodeMirror-activeline-background');
	        			ceeditor.setGutterMarker(prevline, "breakpoints", null);
		        		prevline = data["p"]*1 - 1;
		        		ceeditor.setGutterMarker(prevline, "breakpoints", makeDebugMarker());
		        		ceeditor.addLineClass(prevline, 'background', 'CodeMirror-activeline-background');
		        	}
	        	}
	        };
	    }(tcf), null);
    };
    const term = function(cm) {
		ajaxCall(true, "PUT", "/reports?action=debug&line=-6&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
		        return function(data) {
		        	if(data["s"]===false) {
		        		alert(data);
		        	} else {
		        		cstate = -6;
		        		//alert("Debug session ended");
		        		$.blockUI({ message: '<h3><img src="resources/busy.gif" /> Debugger Disconnecting...</h3>' }, $('.CodeMirror'));
		        		ceeditor.removeLineClass(prevline, 'background', 'CodeMirror-activeline-background');
		        		prevline = 0;
		        	}
		        };
		    }(tcf), null);	
	};
	ceeditor.setOption("extraKeys", {
		"F5": function(cm) {
			ajaxCall(true, "PUT", "/reports?action=debug&line=-1&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile+"&sline="+ceeditor.getLine(prevline), "", "", {}, function(tcf) {
		        return function(data) {
		        	if(data["s"]===false) {
		        		alert(data);
		        	} else {
		        		cstate = -1;
		        		$.blockUI({ message: '<h3><img src="resources/busy.gif" /> Debugger Stepping...</h3>' }, $('.CodeMirror'));
		        		line = data["n"]*1 - 1;
		        		if(prevline!=data["p"]-1) {
			        		ceeditor.removeLineClass(prevline, 'background', 'CodeMirror-activeline-background');
		        			ceeditor.setGutterMarker(prevline, "breakpoints", null);
			        		prevline = data["p"]*1 - 1;
			        		ceeditor.setGutterMarker(prevline, "breakpoints", makeDebugMarker());
			        		ceeditor.addLineClass(prevline, 'background', 'CodeMirror-activeline-background');
			        	}
		        	}
		        };
		    }(tcf), null);
	  	},
		"F7": function(cm) {
			ajaxCall(true, "PUT", "/reports?action=debug&line=-2&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
		        return function(data) {
		        	if(data["s"]===false) {
		        		alert(data);
		        	} else {
		        		cstate = -2;
		        		$.blockUI({ message: '<h3><img src="resources/busy.gif" /> Debugger Stepping...</h3>' }, $('.CodeMirror'));
		        		line = data["n"]*1 - 1;
		        		if(prevline!=data["p"]-1) {
			        		ceeditor.removeLineClass(prevline, 'background', 'CodeMirror-activeline-background');
		        			ceeditor.setGutterMarker(prevline, "breakpoints", null);
			        		prevline = data["p"]*1 - 1;
			        		ceeditor.setGutterMarker(prevline, "breakpoints", makeDebugMarker());
			        		ceeditor.addLineClass(prevline, 'background', 'CodeMirror-activeline-background');
			        	}
		        	}
		        };
		    }(tcf), null);
	  	},
		"F6": function(cm) {
			ajaxCall(true, "PUT", "/reports?action=debug&line=-3&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
		        return function(data) {
		        	if(data["s"]===false) {
		        		alert(data);
		        	} else {
		        		cstate = -3;
		        		$.blockUI({ message: '<h3><img src="resources/busy.gif" /> Debugger Stepping...</h3>' }, $('.CodeMirror'));
		        		line = data["n"]*1 - 1;
		        		if(prevline!=data["p"]-1) {
			        		ceeditor.removeLineClass(prevline, 'background', 'CodeMirror-activeline-background');
		        			ceeditor.setGutterMarker(prevline, "breakpoints", null);
			        		prevline = data["p"]*1 - 1;
			        		ceeditor.setGutterMarker(prevline, "breakpoints", makeDebugMarker());
			        		ceeditor.addLineClass(prevline, 'background', 'CodeMirror-activeline-background');
			        	}
		        	}
		        };
		    }(tcf), null);
	  	},
	  	"F8": function(cm) {
	    	ajaxCall(true, "PUT", "/reports?action=debug&line=-4&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
		        return function(data) {
		        	if(data["s"]===false) {
		        		alert(data);
		        	} else {
		        		cstate = -4;
		        		$.blockUI({ message: '<h3><img src="resources/busy.gif" /> Debugger Running Till Next Breakpoint...</h3>' }, $('.CodeMirror'));
		        		line = data["n"]*1 - 1;
		        		if(prevline!=data["p"]-1) {
			        		ceeditor.removeLineClass(prevline, 'background', 'CodeMirror-activeline-background');
		        			ceeditor.setGutterMarker(prevline, "breakpoints", null);
			        		prevline = data["p"]*1 - 1;
			        		ceeditor.setGutterMarker(prevline, "breakpoints", makeDebugMarker());
			        		ceeditor.addLineClass(prevline, 'background', 'CodeMirror-activeline-background');
			        	}
		        	}
		        };
		    }(tcf), null);
	  	},
	  	'Ctrl-C': stop,
	  	'Cmd-C': stop,
	  	'Ctrl-X': term,
	  	'Cmd-X': term
	});
	if(chkIntv==null) {
		ajaxCall(true, "PUT", "/reports?action=debug&line=0&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
	        return function(data) {
	        	if(data["s"]===false) {
	        		//alert(data["m"]);
	        		clearInterval(chkIntv);
	        	} else {
	        		$.blockUI({ message: '<h3><img src="resources/busy.gif" /> Initializing Debugger...</h3>' });
	        		//$('#debug-controls').css('left', dbgctrl + 'px');
	        		$('#debug-controls').removeClass('hidden');
	        		$('#debug-controls').html('<i key="F8" class="glyphicon glyphicon-play"></i><i key="F6" class="glyphicon glyphicon-step-forward"></i><i key="F5" class="glyphicon glyphicon-save"></i><i key="F7" class="glyphicon glyphicon-open"></i><i key="Ctrl-C" class="glyphicon glyphicon-stop"></i><i key="Ctrl-X" class="glyphicon glyphicon-remove-circle"></i>');
	        		$('#debug-controls').find('i').css('width', '25px');
	        		$('#debug-controls').find('i').css('font-size', 'large');
	        		$('#debug-controls').find('i').css('color', 'indianred');
	        		$('#debug-controls').find('i').on('click', function() {
	        			ceeditor.options.extraKeys[$(this).attr('key')]();
	        		});
	        		ceeditor.getDoc().setValue(data["c"]);
	        		prevline = data["i"]-1;
	        		$('#debug-controls').data("dal", data["l"]);
	        		ceeditor.addLineClass(prevline, 'background', 'CodeMirror-activeline-background');
	        		ceeditor.setGutterMarker(prevline, "breakpoints", makeDebugMarker());
	        		chkIntv = setInterval(function() {
	        			if(!$('#req-txtarea').data('tcf')) {
	        				clearInterval(chkIntv);
							chkIntv = undefined;
	        				ceeditor = undefined;
	        				$('#debug-controls').addClass('hidden');
					        //document.removeEventListener('keypress',  cmkp);
	        			}
				    	ajaxCall(false, "PUT", "/reports?action=debug&line=-7&testcaseFileName=" + tcf + "&testCaseName=" + tc + isserverlogfile, "", "", {}, function(tcf) {
					        return function(data) {
					        	if(data["s"]===false) {
	        						alert(data["m"]);
					        		$('#debug-controls').addClass('hidden');
					        		clearInterval(chkIntv);
									chkIntv = undefined;
									$('a.asideLink[tcfname="'+currtestcasefile+'"]').trigger('click');
					        		ceeditor = undefined;
					        		openInDebugMode = undefined;
					        	} else {
					        		if(data["r"]===false) {
					        			alert("Debug session completed");
					        			$('#debug-controls').addClass('hidden');
										clearInterval(chkIntv);
										chkIntv = undefined;
										$('a.asideLink[tcfname="'+currtestcasefile+'"]').trigger('click');
					        			ceeditor = undefined;
					        			//document.removeEventListener('keypress',  cmkp);
					        			openInDebugMode = undefined;
					        		} else {
						        		line = data["n"]*1 - 1;
						        		if(data["v"]==-5) {
						        			$.unblockUI(undefined, $('.CodeMirror'));
						        		}
						        		if(currtestcasefile!=data["t"]) {
						        			currtestcasefile = data["t"];
						        			$('a.asideLink[tcfname="'+currtestcasefile+'"]').trigger('click');
						        			openInDebugMode = data;
						        		} else if(cstate && data["v"]===cstate) {
							        		ceeditor.removeLineClass(prevline, 'background', 'CodeMirror-activeline-background');
						        			ceeditor.setGutterMarker(prevline, "breakpoints", null);
							        		prevline = data["n"]*1 - 1;
							        		ceeditor.setGutterMarker(prevline, "breakpoints", makeDebugMarker());
							        		ceeditor.addLineClass(prevline, 'background', 'CodeMirror-activeline-background');
						        			$.unblockUI(undefined, $('.CodeMirror'));
						        			cstate = undefined;
							        	}
					        		}
					        	}
					        };
					    }(tcf), function(err) {
			        		$('#debug-controls').addClass('hidden');
			        		clearInterval(chkIntv);
							chkIntv = undefined;
							$('a.asideLink[tcfname="'+currtestcasefile+'"]').trigger('click');
			        		ceeditor = undefined;
			        		openInDebugMode = undefined;
					    });
				    }, 5000);
	        	}
	        };
	    }(tcf), null);
	}
}

function getErroredSeleasyScripts() {
	$('.top_sel_but').hide();
    $('#heading_main').html('Errored Scripts');
    $('#ExampleBeanServiceImpl_form').html('');
    if(errdFilesReport && Object.keys(errdFilesReport).length>0) {
		var htm = '<table id="errdselscr" class="table table-striped table-bordered table-hover" width="100%" style="width:100%;table-layout:fixed;word-wrap:break-word;color:black">';
        var ttable;
        htm += '<thead><tr><th style="color:black">File</th><th style="color:black">Line No</th></tr></thead><tbody>';
		for(const v of Object.keys(errdFilesReport)) {
			htm += '<tr><td>'+v+'</td><td class="errdss">'+errdFilesReport[v][0][0]+'</td></tr>';
		}
		htm += '</tbody></table><p>&nbsp;</p><p>&nbsp;</p>';
        $('#ExampleBeanServiceImpl_form').append(htm);
        ttable = $('#errdselscr').dataTable({
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
        $('.errdss').on('click', function() {
			$('[tcfname="'+($(this).siblings().eq(0).text())+'"]').trigger('click');
			if($('.blockUI').length==0) $.blockUI({ message: '<h3><img src="resources/busy.gif" /> Just a moment...</h3>' });
			setTimeout(function(ele) {
				return function() {
					function makeMarker() {
						var marker = document.createElement("div");
						marker.style.color = "red";
						marker.innerHTML = "❌";
						return marker;
					}
					ceeditor.setGutterMarker(ele.text()*1-1, "breakpoints", makeMarker());
					$.unblockUI();
				};
			}($(this)), 2000);
		});
	} else {
		$('#ExampleBeanServiceImpl_form').html('No Errored Seleasy scripts found...');
	}
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
	if(!isNaN(s)) return s;
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
            value = escapeHtml1(value);
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

    var content = '<div><p>Request:</p><pre>Curl: ' + report.curlCmd + '</pre><pre>Actual Url: ' + report.actualUrl + '</pre><pre>Template Url: ' + report.url + '</pre><pre>' + reqhdrsVal + '</pre>';
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
	$('#editorTabs').html('').addClass('hidden');
    $('#heading_main').html('Generator Configurationuration');
    var configschema = JSON.parse('{"type":"object","properties":{"testPaths":{"type":"array","items":{"nolabel":true,"type":"string"}},"soapWsdlKeyPairs":{"type":"array","items":{"nolabel":true,"type":"string"}},"urlPrefix":{"type":"string"},"requestDataType":{"type":"string"},"responseDataType":{"type":"string"},"resourcepath":{"type":"string"},"enabled":{"type":"boolean","required":true},"overrideSecure":{"type":"boolean","required":true},"useSoapClient":{"type":"boolean","required":true},"urlSuffix":{"type":"string"},"postmanCollectionVersion":{"type":"integer"},"testCaseFormat":{"type":"string"}}}');
    ajaxCall(true, "GET", "configure?configType=generator", "", "", {}, function(configschema) {
        return function(data) {
            countMap = {};
            $('#ExampleBeanServiceImpl_form').html(generateFromValue(configschema, '', true, '', '', data, false, true, true, ''));
            prepareForm('configure?configType=generator', 'POST', jQuery.isEmptyObject(data) ? '💾' : '💾', null, null);
			initEvents($('#ExampleBeanServiceImpl_form'));
        };
    }(configschema), null);
}

function configuration() {
	$('.top_sel_but').hide();
	errdFilesReport = {};
	$('#editorTabs').html('').addClass('hidden');
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
            "mailSimulator": {
                "label": {
                    "type": "section",
                "value": "Mail Simulator",
                "border": true
                },
                "type": "object",
                "properties": {
                    "enabled": {
                        "type": "boolean"
                    },
                    "login": {
                        "type": "string"
                    },
                    "password": {
                        "type": "string"
                    },
                    "isSecure": {
                        "type": "boolean"
                    },
                    "smtpPort": {
                        "type": "integer"
                    },
                    "imapPort": {
                        "type": "integer"
                    }
                }
            },
            "htttpServerSimulator": {
                "label": {
                    "type": "section",
                    "value": "HTTP Simulator",
                    "border": true
                },
                "type": "object",
                "properties": {
                    "enabled": {
                        "type": "boolean"
                    },
                    "port": {
                        "type": "integer"
                    },
                    "headers": {
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
                    "body": {
                        "type": "string"
                    }
                }
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
                "enumVar": "miscMap.providers"
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
            "selConcWebdriver": {
                "type": "boolean",
                "required": false
            },
            "isSeleniumModuleTests": {
                "type": "boolean",
                "required": false
            },
            "seleniumScript": {
                "type": "string",
                "required": false
            },
            "seleniumScriptRetryCount": {
                "type": "integer",
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
                        "arguments": {
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
                                    "enumVar": "miscMap.datasourcecls"
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
                                    "enumVar": "miscMap.datasourcecls"
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
                                    "enumVar": "miscMap.datasources"
                                },
                                "providerName": {
                                    "type": "string"
                                },
                                "providerClass": {
                                    "type": "string",
                                    "enumVar": "miscMap.providercls"
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
                                    "enumVar": "miscMap.datasources"
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
            },
            "extraProperties": {
                "label": {
                    "type": "section",
                    "value": "Additional Properties"
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
    };
    ajaxCall(true, "GET", "configure?configType=executor", "", "", {}, function(configschema) {
        return function(data) {
            countMap = {};
            isSeleniumExecutor = data.isSeleniumExecutor
            $('#ExampleBeanServiceImpl_form').html(generateFromValue(configschema, '', true, '', '', data, false, true, true, ''));
            prepareForm('configure?configType=executor', 'POST', jQuery.isEmptyObject(data) ? '💾' : '💾', "onUpdConfig", "onUpdConfigFail");
			initEvents($('#ExampleBeanServiceImpl_form'));
        };
    }(configschema), null);
}
function onUpdConfigFail(data) {
	showErrorAlert(data.responseText);
	configuration();
}
function onUpdConfig(data) {
	isSeleniumExecutor = data.responseJSON.isSeleniumExecutor;
	startInitConfigTool();
	showSuccessAlert("Configuration saved Successfully...");
	//window.scrollTo({top: $('#buttons_cont').offset().top-120, behavior: 'smooth'});
	//$("html, body").animate({ scrollTop: $(document).height() }, 1000);
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
    var url = $('#93be7b20299b11e281c10800200c9a66_URL').val();
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
	$('.top_sel_but').hide();
	errdFilesReport = {};
    ajaxCall(true, "GET", "reports?action=paths&type=api", "", "", {}, function(data) {
		if(data && data['paths'] && data.paths.length>0)
			document.location = "/reports/"+Object.values(data.paths[0])[0][0].left+"/index.html";
		else
			alert("No Reports found...");
    }, function(data) {
        alert("No Reports found...");
    });
    return false;
}

function getSeleniumReports() {
	$('.top_sel_but').hide();
	errdFilesReport = {};
    ajaxCall(true, "GET", "reports?action=paths&type=sel", "", "", {}, function(data) {
		if(data && data['paths'] && data.paths.length>0)
			document.location = "/reports/"+Object.values(data.paths[0])[0][0].left+"/selenium-index.html";
		else
			alert("No Reports found...");
    }, function(data) {
        alert("No Reports found...");
    });
    return false;
}

function searchLeftNavs(ele) {
    var term = ele.target.value.trim();
    /*$('.accordion-toggle').each(function() {
        if (term == '') {
            $(this).parent().show();
        } else {
            if ($(this).text().search(new RegExp(term, "i")) == -1) {
                $(this).parent().hide();
            }
        }
    });*/
    $('.accordion-body').removeClass('in').addClass('out');
    $('.accordion-inner').each(function() {
        if (term == '') {
            //$(this).parent().show();
            $('.accordion-body').removeClass('out').addClass('in');
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
                $(this).parent().removeClass('out').addClass('in');
                $(this).parent().parent().children().eq(0).show();
            }
        }
    });
}

function ajaxCall(blockUi, meth, url, contType, content, vheaders, sfunc, efunc) {
	if(blockUi) blkcount++;
	//console.log(blkcount);
    if (blockUi) {
	    if($('.blockUI').length==0) $.blockUI({message: '<h3><img src="resources/busy.gif" /> Just a moment...</h3>'});
	}
    $.ajax({
        headers: vheaders,
        type: meth,
        processData: false,
        url: url,
        contentType: contType,
        data: content
    }).done(function(msg, statusText, jqXhr) {
        var data = jqXhr.responseText;
        try {
            data = JSON.parse(jqXhr.responseText);
        } catch (err) {
            data = jqXhr.responseText;
        }
        sfunc(data, jqXhr);
        if(blockUi) blkcount--;
        //console.log(blkcount);
        if (blockUi && blkcount==0) $.unblockUI();
    }).fail(function(jqXhr, textStatus, msg) {
        if (efunc == null) alert(jqXhr.responseText);
        var data = jqXhr.responseText;
        try {
            data = JSON.parse(jqXhr.responseText);
        } catch (err) {
            data = jqXhr.responseText;
        }
        if (efunc != null) efunc(data, jqXhr);
        if(blockUi) blkcount--;
        //console.log(blkcount);
        if (blockUi && blkcount==0) $.unblockUI();
    });
}

function handleChgEvent(ths) {
	hideShowClasses('form-elems','form-request_content', ths);
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
	$('#select_all_tcs').on('click', function() {
		//let all = $('#select_all_tcs').is(":checked");
		let tests = $(this).closest('form').find('table').find('tr');
		if($(this).is(":checked")) {
			for(const tr of tests) {
				$(tr).first('td').find('input').prop('checked', true);
			}
		} else {
			for(const tr of tests) {
				$(tr).first('td').find('input').prop('checked', false);
			}
		}
	});
}

function execTc(method, succFunc, failFunc) {
	executeTest('#93be7b20299b11e281c10800200c9a66_URL', method.toUpperCase(), 'application/json', '#ExampleBeanServiceImpl_form', succFunc, failFunc);
	return false;
}

function prepareForm(url, method, buttonLabel, succFunc, failFunc, isSelfContained, eid) {
    var htm = '';
    if (!isSelfContained || isSelfContained === false) {
        htm += '<div class="control-group"> \
					<label>Use Raw Text:&nbsp;</label> \
					<div class="controls"><input id="raw_req_cont_flag" type="checkbox" change-event="handleChgEvent(this)"/></div> \
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
    let cls = buttonLabel=="Update"?'bigb':'';
    let rght = 70;
    let bthm = '<button type="button" style="position: absolute;left: 20px;top: 5px;" click-event="gotoBottom()">↓</button> \
					<button type="button" style="position: absolute;right: '+rght+'px;top: 5px;" class="org_save_butt ' + method.toLowerCase() + cls + '" click-event="execTc(\''+method.toLowerCase()+'\', '+succFunc+', '+failFunc+')">' + buttonLabel + '</button> \
					<button type="button" style="position: absolute;left: 20px;bottom: 25px;" click-event="gotoTop()">↑</button> \
					<button type="button" style="position: absolute;right: 20px;bottom: 25px;" class="' + method.toLowerCase() + cls + '" click-event="execTc(\''+method.toLowerCase()+'\', '+succFunc+', '+failFunc+')">' + buttonLabel + '</button>';
    $('#ExampleBeanServiceImpl_form').append(
        '<div class="control-group"> \
				<label class=""></label> \
				<div class="controls" id="buttons_cont">' + bthm + '</div> \
			</div> \
			<br/><br/><p></p><br/> \
			<div id="api_execution_reponse" style="display:none"> \
			<label>Response Time&nbsp;</label><span id="restime"></span><br/><br/> \
			<label>Response Headers:&nbsp;</label><div><pre id="reshdrs" style="word-wrap:break"></pre></div> \
			<label style="word-wrap:break-word;margin-left:50px;width:auto;background-color: #ebf4fb;border:none;"></label><div><br/><pre id="status" class="prettyprint" style="word-wrap:break-word;margin-left:50px;width:auto;border:none;"></pre></div><br/></div>');
}

function gotoTop() {
	$("html, body").animate({ scrollTop: 0 }, 1000);
}

function gotoBottom() {
	 window.scrollTo({top: $('#buttons_cont').offset().top-120, behavior: 'smooth'});
	//$("html, body").animate({ scrollTop: 0 }, 1000);
}

function gatfHLCodeMirror() {
    $('.CodeMirror-line').each(handleCommand);
}

function handleCommandCodeMirror() {
    const presentation = $(this).find('span[role="presentation"]');
    const contents = presentation.contents();
    let first = contents.eq(0);
    let c = 0;
    if(first.length>0 && first.hasClass('cm-tab')) {
        while(first.length>0 && first.hasClass('cm-tab') && c<contents.length) {
            first = contents.eq(c++);
        }
    }
    if(first.length>0 && first[0].nodeName=="#text" && first[0].data.trim()=="") {
        while(first.length>0 && first[0].nodeName=="#text" && first[0].data.trim()=="" && c<contents.length) {
            first = contents.eq(c++);
        }
    }
    if(!first.hasClass('cm-tab') && !(first[0].nodeName=="#text" && first[0].data.trim()=="")) {
        //console.log(contents);
        let cmd = "";
        for(let i=c;i<contents.length;i++) {
            cmd += contents.eq(i)[0].nodeName=="#text"?contents.eq(i)[0].data.trim():contents.eq(i).text();
        }
        console.log(cmd);
    }
}