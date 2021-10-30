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

function escapeHtml(s) {
	return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function printResponse(msg, contentTypeHeader)
{
	var resp = msg;
	if(contentTypeHeader!=null && contentTypeHeader.indexOf('application/json')!=-1)
	{
		resp = syntaxHighlight(msg);
	}
	else if(contentTypeHeader!=null && contentTypeHeader.indexOf('xml')!=-1)
	{
		resp = vkbeautify.xml(resp);
		resp = escapeHtml(resp);
		resp = prettyPrintOne(resp);
	}
	return resp;
}

function getTestResultContent(report, index)
{
	var reqhdrsVal = getData(report.requestHeaders, 'text/plain');
		
	var rescnttyp = 'text/plain';
	if(undefined!=report.responseContentType && report.responseContentType!=null)
		rescnttyp = report.responseContentType;

	var responseHeaders = getData(report.responseHeaders, 'text/plain');
	
	var reqcnttyp = 'text/plain';
	if(undefined!=report.requestContentType && report.requestContentType!=null)
		reqcnttyp = report.requestContentType;

	var content = '<div>';
	if(report.status=='Failed')
	{
		var trid = '#tr_report_'+index;
		content += '<p><button click-event="getIssueDetails(\''+trid+'\',\''+index+'\')">Create Issue</button></p>';
	}
	content += '<p>Request:</p><pre>Actual Url: '+report.actualUrl+'</pre><pre>Template Url: '+report.url+'</pre><pre>'+reqhdrsVal+'</pre>';
	content += '<pre>'+getData(report.requestContent, reqcnttyp)+'</pre></div>';
	content += '<div><p>Response:</p><pre>'+responseHeaders+'</pre>';
	if(report.responseStatusCode!=undefined)content += '<pre>Status Code: '+report.responseStatusCode+'</pre>';
	if(report.serverLogs!=undefined)content += '<pre>Server Log: '+report.serverLogs+'</pre>';
	content += '<pre>'+getData(report.responseContent, rescnttyp)+'</pre></div>';

	var error = getData(report.error, 'text/plain');
	if(undefined!=report.errors && report.errors!=null)
	{
		var err = '';
		for (var key in report.errors) {
			if (report.errors.hasOwnProperty(key)) {
				err += ('Run ' + key + "\n" + report.errors[key] + "\n\n");
			}
		}
		if(err!='')
			error = err;
	}
	if(error!='NA')
	{
		error = escapeHtml(error);
		content += '<div><p>Errors:</p><pre>'+error+'</pre>';
		content += '<pre>'+escapeHtml(getData(report.errorText, 'text/plain'))+'</pre></div>';
	}
	return content;
}

function execFunction1(evt) {
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
			} else if(tmp=="this") {
				args.push($(e.target));
			} else if(tmp=="event") {
				args.push($(e));
			} else if(tmp=='true' || tmp=='false') {
				args.push(tmp=='true');
			} else if(!isNaN(tmp)) {
				args.push(tmp*1);
			} else if(tmp.startsWith("$(")) {
				tmp = tmp.substring(2, tmp.lastIndexOf(")"));
				args.push($(tmp.substring(1, tmp.length-1)));
			}
		}
	}
	if(window[fnm]) {
		window[fnm].apply(null, args);
	}
}

var dtdatatable = null;
function showTable(reportType, type, perfType)
{
	if(dtdatatable)
	{
		dtdatatable.destroy();
	}
	if(reportType==null)
	{
		reportType = "Performance";
	}
	var reports = getTableVals(reportType, type, perfType);
	$('#dataTables-example tbody').html(' ');
	$.each(reports, function(i, item) {
		var actualRequest = {actualUrl: reports[i].actualUrl, requestContent: reports[i].requestContent, requestHeaders: reports[i].requestHeaders, aexpectedNodes: reports[i].aexpectedNodes};
		var reqhdrsVal = getData(reports[i].requestHeaders, 'text/plain');
		
		var rescnttyp = 'text/plain';
		if(undefined!=reports[i].responseContentType && reports[i].responseContentType!=null)
			rescnttyp = reports[i].responseContentType;

		var responseHeaders = getData(reports[i].responseHeaders, 'text/plain');
		
		var reqcnttyp = 'text/plain';
		if(undefined!=reports[i].requestContentType && reports[i].requestContentType!=null)
			reqcnttyp = reports[i].requestContentType;
						
		var perfcont = '';					
		var execTimedis = reports[i].executionTime;
		if(undefined!=reports[i].averageExecutionTime && reports[i].averageExecutionTime!=null)
		{
			execTimedis += '<br/><u style="color:black">' + reports[i].numberOfRuns + ' runs<u>';
			execTimedis += '<br/><u style="color:black">' + reports[i].averageExecutionTime + ' ms (avg)<u>';

			perfcont += '<div><p>Statistics: <u style="color:black;font-size:0.90em">Note: For Performance Tests, Request/Response details are taken from the first execution only</u></p><pre>Number of Runs: '+reports[i].numberOfRuns+'</pre>';
			perfcont += '<pre>Total Execution Time: '+reports[i].executionTime+' ms</pre>';
			perfcont += '<pre>Execution Times: '+reports[i].executionTimes.join(' ms, ')+' ms</pre>';
			perfcont += '<pre>Average Execution Time: '+reports[i].averageExecutionTime+' ms</pre></div>';
		}

		var content = '<div id="tddatah-'+i+'">Request, Response Details...</div><div id="tddata-'+i+'" style="display:none">'+perfcont;
		content += getTestResultContent(reports[i], i);
		content += '</div>';

		var identi = reports[i].testIdentifier;
		var idenprt = identi.split("\n");
		identi = '<b>' + idenprt[0] + '</b><br/>';
		for(var t=1;t<idenprt.length;t++)
			identi += idenprt[t] + '<br/>';

		var tcf = '';
		if(idenprt.length<=2)
			tcf = idenprt[0];
		else
			tcf = idenprt[1];

		var tc = '';
		if(idenprt.length<=2)
			tc = idenprt[1];
		else
			tc = idenprt[2];

		var glyph = 'glyphicon glyphicon-ok';
		var tr = '<tr id="tr_report_'+i+'" stat="Success">';
		var trid = '#tr_report_'+i;
		var reasonCd = "";
		if(reports[i].status=='Failed')
		{
			glyph = 'glyphicon glyphicon-remove';
			tr = '<tr id="tr_report_'+i+'" stat="Failed">';
			//trid = '#dttbtd_'+i;
			reasonCd = "<br/>"+reports[i].failureReason;
			actualRequest = reports[i];
		}
		else if(reports[i].status=='Skipped')
		{
			glyph = 'glyphicon glyphicon-minus';
			tr = '<tr id="tr_report_'+i+'" sid="dttbtd-'+i+'" stat="Skipped">';
			reasonCd = "";
			if(reports[i].failureReason!=undefined)
				reasonCd = "<br/>"+reports[i].failureReason;
			else
				reasonCd = "<br/>Test Skipped";
		}	
		$('#dataTables-example tbody').append($(tr));
		$('#tr_report_'+i).html(
		"<td>" + identi + "</td><td><p><a href=\"#\" click-event=\"replayTest('"+trid+"',"+i+")\">Replay</a></p>"+reports[i].method+"</td><td><span class=\""+glyph+"\"></span>"+reasonCd+"</td><td>" + execTimedis + "</td><td data-id=\"tddata-"+i+"\" data-hid=\"tddatah-"+i+"\" style=\"cursor:pointer;cursor:hand;\" click-event=\"openTestCaseDetails(event, this)\">"+content+"</td>");
		$('#tr_report_'+i).find('[click-event]').off().on('click', function() {
			var evt = $(e.target).attr('click-event');
			execFunction1(evt);
		});
		$(trid).data('tcf', tcf);
		$(trid).data('tc', tc);
		$(trid).data('actualRequest', actualRequest);
	});
	dtdatatable = $('#dataTables-example').DataTable({
		"columns": [
						{"width" : "20%","sType": "alphanum"},
						{"width" : "6%"},
						{"width" : "12%"},
						{"width" : "8%"},
						{"width" : "50%"}
					 ],
		"order" : [],
		"paging": true,
		"searching": true,
		"ordering": true,
		"bJQueryUI": true,
		"rowCallback": function( nRow, aData, iDisplayIndex, iDisplayIndexFull ) {
			if('Skipped' == $(nRow).attr('stat'))
			{
				$(nRow).css("color", "orange");
			}
			else if('Failed' == $(nRow).attr('stat'))
			{
				$(nRow).css("color", "red");
			}
			else
			{
				$(nRow).css("color", "green");
			}
		},
		"dom": 'Blfrtip',
        "buttons": [
            'copy', 'csv', 'excel', 'pdf', 'print'
        ]
	});
	dtdatatable.column(0).order('asc').draw();
	shortHandTableHeaders('dataTables-example', 20, 'td');
}

function getData(value, contentType)
{
	if(undefined == value  || value == null || value == '')
		return 'NA';
	else
	{
		var jsonobj = null;
		try {
		  jsonobj = JSON.parse(value);
		  value = JSON.stringify(jsonobj, undefined, 4);
		  contentType = 'application/json';
		} catch (e) {
		  //console.error("Parsing error:", e); 
		}
		return printResponse(value, contentType);				
	}
}

function shortHandTableHeaders(tableID, limit, wh) {

	var ths = $('#' + tableID + ' tbody tr '+wh);        
	var content;
	
	ths.each (function () {
		var $this = $(this);
		$this.off("click").click (
		   function() {
				var id = $(this).attr('data-id');
				var hid = $(this).attr('data-hid');
				if($('#'+id).is(":visible"))
				{					
					$('#'+id).hide();
					$('#'+hid).show();
					var panelHeight =	$(dtdatatable).parent().height();
					if($('#dtiframe'))
						$('#dtiframe').css('height', (panelHeight*1+150)+'px');			
					window.parent.postMessage(["setHeight", panelHeight], "*");
				}
				else
				{
					$('#'+id).show();
					$('#'+hid).hide();
					var panelHeight =	$(dtdatatable).parent().height();
					if($('#dtiframe'))
						$('#dtiframe').css('height', (panelHeight*1+150)+'px');	
					window.parent.postMessage(["setHeight", panelHeight], "*");
				}
		   }
		);
	});						
}

function openTestCaseDetails(e, ele)
{
	var id = ele.getAttribute('data-id');
	var hid = ele.getAttribute('data-hid');
	if($('#'+id).is(":visible"))
	{
		$('#'+id).hide();
		$('#'+hid).show();
		var panelHeight =	$(dtdatatable).parent().height();
		if($('#dtiframe'))
			$('#dtiframe').css('height', (panelHeight*1+150)+'px');			
		window.parent.postMessage(["setHeight", panelHeight], "*");
	}
	else
	{
		$('#'+id).show();
		$('#'+hid).hide();
		var panelHeight =	$(dtdatatable).parent().height();
		if($('#dtiframe'))
			$('#dtiframe').css('height', (panelHeight*1+150)+'px');			
		window.parent.postMessage(["setHeight", panelHeight], "*");
	}
	e.stopImmediatePropagation();
  return false;
}

function shortHandHeaderTxt(txt, limit) {
	return txt.substring(0, limit - 2) + "..";
}
var flotpiechart = null;
var flotpiechart2 = null;
function showPieChart(reportType, isTests) {
	if(flotpiechart) {
		flotpiechart.destroy();
	}
	if(flotpiechart2) {
		flotpiechart2.destroy();
	}
	var sin = getPieVals(reportType, isTests);
	var succv = 0;
	if(sin.hasOwnProperty('skipped')) {
		succv = sin.total - sin.failed - sin.skipped;
	}
	var data = [{
		label: "Success",
		data: succv, 
		color : 'green'
	}, {
		label: "Failed",
		data: sin.failed, 
		color : 'red'
	}, {
		label: "Skipped",
		data: sin.skipped, 
		color : 'orange'
	}];

	var pieId = "#flot-pie-chart";
	if(!isTests)
	{
		pieId = "#flot-pie-chart2";
		$('#secondpie').show();
	}
	var flpie = null;
	flpie = $.plot($(pieId), data, {
		series: {
			pie: {
				show: true,
				label: {
					show: true,
					radius: 1.5 / 3,
					formatter: function (label, series) {
						return '<div style="font-size:8pt;text-align:center;color:white;">' + label + '<br/>'+ series.data[0][1] + '</div>';
					},
					threshold: 0.1
				}
			}
		},
		grid: {
			hoverable: true,
			clickable: true,
			canvasText: {show: true, font: "sans 8px"}
		},
		tooltip: true,
		tooltipOpts: {
			content: "%p.0%, %s", // show percentages, rounding to 2 decimal places
			shifts: {
				x: 20,
				y: 0
			},
			defaultTheme: false
		}
	});
	
	if(isTests)
	{
		$(pieId).off("plotclick").bind("plotclick", function(event, pos, obj) {
			if(!obj)return;
			document.getElementById('dtiframe').contentWindow.postMessage(["filterTestReport", reportType+","+obj.series.label], "*");
			//showLineGraph(reportType, obj.series.label);
			showTable(reportType,obj.series.label,null);
			var currv = $('#groupFilesSelect').val();
			if((userSimulation || compareEnabled) && currv=='All')
			{
				if(multlinegrpwid)
					$("#flot-line-chart").width(multlinegrpwid);
				showMultiLineGraph();
			}
			else
			{
				if(linegrpwid)
					$("#flot-line-chart").width(linegrpwid);
				showLineGraph(reportType, obj.series.label);
			}
		});
	}
	else
	{
		$(pieId).off("plotclick").bind("plotclick", function(event, pos, obj) {
			if(!obj)return;
			document.getElementById('dtiframe').contentWindow.postMessage(["filterOtherReport", reportType+","+obj.series.label], "*");
			showTable(null,obj.series.label,reportType);
		});
	}
	if(!isTests)
	{
		flotpiechart2 = flpie;
	}
	else
	{
		flotpiechart = flpie;
	}
}

function magnify(isPlus, id, scale, isParent) {
	if(isPlus)
	{
		if(isParent)
			$("#"+id).width($("#"+id).parent().width()*scale);
		else
			$("#"+id).width($("#"+id).width()*scale);
	}
	else
	{
		if(isParent)
			$("#"+id).width($("#"+id).parent().width()/scale);
		else
			$("#"+id).width($("#"+id).width()/scale);
	}
}

function showHide(eleId) {
	if($('#'+eleId).is(':visible'))
		$('#'+eleId).hide();
	else
		$('#'+eleId).show();
}

function showTooltip(x, y, color, contents) {
	var bwid = $('body').innerWidth();
	
    $('<div id="tooltip">' + contents + '</div>').css({
        position: 'absolute',
        display: 'none',
        top: y - 40,
        left: x,
        border: '2px solid ' + color,
        padding: '3px',
            'font-size': '9px',
            'border-radius': '5px',
            'background-color': '#fff',
            'font-family': 'Verdana, Arial, Helvetica, Tahoma, sans-serif',
        opacity: 0.9
    }).appendTo("body").fadeIn(200);

	var tipwid = $('#tooltip').innerWidth();
	if(x+tipwid>bwid)
		$('#tooltip').css({left:x-tipwid-30, right:bwid-x});
}

var previousPoint = null, previousLabel = null;
var linegrpwid = 0, multlinegrpwid = 0;
var isLineChart = true;
var flotlinchart = null;
function showLineGraph(reportType, type) {
	var test = getLineVals(reportType, type);	
	var sin = [];

	var alldat = []
	$('#tempodata').html('');
	var tr = null;
	var sinlength = test.length;
	for (var i = 0; i < test.length; i++) {
		sin.push([(i+1), test[i].executionTime]);
		var identi = test[i].identifier;
		if(test[i].identifier!=test[i].sourceFileName)
			identi += '\n' + test[i].sourceFileName;
		if(tr==null || i%3==0)
		{
			tr = $('<tr/>').appendTo('#tempodata');
			tr.append('<td style="word-wrap: break-word"><pre style="border:none">'+(i+1) + '\n' 
				+ identi + '\n' + test[i].testCaseName +'</pre></td>');
		}
		else
		{
			tr.append('<td style="word-wrap: break-word"><pre style="border:none">'+(i+1) + '\n'
				+ identi + '\n' + test[i].testCaseName +'</pre></td>');
		}
	}
	alldat.push({label:reportType, data: sin});

	if(sinlength>0 && isLineChart)
	{
		if(ovStats!=null && ovStats[reportType]!=undefined)
		{
			var hsinn = [];
			var hsinf = [];
			for (var hh=0;hh<sinlength;hh++)
			{
				hsinn.push([(hh+1), ovStats[reportType][0]]);
				hsinf.push([(hh+1), ovStats[reportType][1]]);
			}
			if(is90)alldat.push({label:'90% ('+ovStats[reportType][0]+' ms)', data: hsinn});
			if(is50)alldat.push({label:'50% ('+ovStats[reportType][1]+' ms)', data: hsinf});
		}
	}

	if(tr!=null)
		$('#tempodata').append(tr);

	var sers = null;
	if($('#labelcheck').is(':checked'))
	{
		sers = [[0, function(contextObj, scopeObj){
                var r = {};
                r.leftOffset = 20;
                r.topOffset = -20;
                r.label = scopeObj[contextObj.index].testCaseName;
                r.color = "#grid";
                return r;
            }, test]];
	}
	else if($('#valuelcheck').is(':checked'))
	{
		sers = [[0, function(contextObj, scopeObj){
                var r = {};
                r.leftOffset = 20;
                r.topOffset = -20;
                r.label = contextObj.y;
                r.color = "#grid";
                return r;
            }, test]];
	}

	if(flotlinchart) {
		flotlinchart.destroy();
	}

	if(isLineChart)
	{
		flotlinchart = $.plot("#flot-line-chart", alldat, {
			series: {
				lines: {
					show: true
				},
				points: {
					show: true
				}
			},
            grid: {
                hoverable: true, //IMPORTANT! this is needed for tooltip to work
				canvasText: {show: true, font: "sans 8px", series: sers},
				backgroundColor: { colors: ["#fff", "#eee"]}
            },
			xaxis: {
				min: 0,
				mode: "categories"
			}
		});
		$("#flot-line-chart").off("plothover").bind("plothover", function(event, pos, item) {
			if (item) {
				if ((previousLabel != item.series.label) || (previousPoint != item.dataIndex)) {
					previousPoint = item.dataIndex;
					previousLabel = item.series.label;
					$("#tooltip").remove();

					var x = item.datapoint[0];
					var y = item.datapoint[1];
					var color = item.series.color;

					showTooltip(item.pageX, item.pageY, color,
						"<strong>" + item.series.xaxis.ticks[x].label + " - " + test[previousPoint].testCaseName + "</strong><br><strong>" + y + " ms</strong>");
				}
			} else {
				$("#tooltip").remove();
				previousPoint = null;
			}
		});
	}
	else
	{
		flotlinchart = $.plot("#flot-line-chart", alldat, {
			series: {
				bars: {
					show: true,
					barWidth: 0.05,
					align: "center"
				}
			},
            grid: {
                hoverable: true, //IMPORTANT! this is needed for tooltip to work
				canvasText: {show: true, font: "sans 8px", series: sers},
				backgroundColor: { colors: ["#fff", "#eee"]},
            },
			xaxis: {
				min: 0,
				mode: "categories",
				tickLength: 0
			}
		});
		$("#flot-line-chart").off("plothover").bind("plothover", function(event, pos, item) {
			if (item) {
				if ((previousLabel != item.series.label) || (previousPoint != item.dataIndex)) {
					previousPoint = item.dataIndex;
					previousLabel = item.series.label;
					$("#tooltip").remove();

					var x = item.datapoint[0];
					var y = item.datapoint[1];
					var color = item.series.color;

					showTooltip(item.pageX, item.pageY, color,
						"<strong>" + item.series.xaxis.ticks[x].label + " - " + test[previousPoint].testCaseName + "</strong><br><strong>" + y + " ms</strong>");
				}
			} else {
				$("#tooltip").remove();
				previousPoint = null;
			}
		});
	}	
	if(!multlinegrpwid)multlinegrpwid = $("#flot-line-chart").width();
	if(!linegrpwid)linegrpwid = $("#flot-line-chart").width();
	if(test.length>30) {
		var scale = test.length/30;
		magnify(true, 'flot-line-chart', scale, true);
	} else if(linegrpwid>0) {
		$("#flot-line-chart").width(linegrpwid);
	}
	lastPointsNum = test.length;
	//$("#flot-line-chart").find("div").remove();
}
var flotlinmultichart = null;
function showMultiLineGraph() {
	
	var alldat = [], eventData = [];
	$('#tempodata').html('');
	var tr = null;
	var xlen = 0;
	var sinlength = 0;	

	var sers = null;
	if($('#labelcheck').is(':checked') || $('#valuelcheck').is(':checked'))
	{
		sers = new Array();
	}

	for(var i=0;i<suiteStats.groupStats.length;i++)
	{
		var test = getLineVals(suiteStats.groupStats[i].sourceFile, 'All');
		var sin = [];
		for (var j = 0; j < test.length; j++) {
			sin.push([(j+1), test[j].executionTime]);
			if(i==0)
			{
				xlen = test.length;
				if(tr==null || j%3==0)
				{
					tr = $('<tr/>').appendTo('#tempodata');
					tr.append('<td style="word-wrap: break-word"><pre style="border:none">'+(j+1) + '\n' 
						+ test[i].identifier + '\n' + test[i].sourceFileName + '\n' + test[j].testCaseName +'</pre></td>');
				}
				else
					tr.append('<td style="word-wrap: break-word"><pre style="border:none">'+(j+1) + '\n' 
						+ test[i].identifier + '\n' + test[i].sourceFileName + '\n' + test[j].testCaseName +'</pre></td>');
			}
		} 
		sinlength = sin.length;
		eventData.push(test);
		if(compareEnabled)
			alldat.push({label:suiteStats.groupStats[i].baseUrl, data: sin});
		else
			alldat.push({label:suiteStats.groupStats[i].sourceFile, data: sin});
		
		if($('#valuelcheck').is(':checked'))
		{
			var tys = [i, function(contextObj, scopeObj){
					var r = {};
					r.leftOffset = 20;
					r.topOffset = -20;
					r.label = contextObj.y;
					r.color = "#grid";
					return r;
				}];
			sers.push(tys);
		}
		else if($('#labelcheck').is(':checked'))
		{
			var tys = [i, function(contextObj, scopeObj){
					var r = {};
					r.leftOffset = 20;
					r.topOffset = -20;
					r.label = scopeObj[contextObj.index].testCaseName;
					r.color = "#grid";
					return r;
				}, test];
			sers.push(tys);
		}
	}
	if(tr!=null)
		$('#tempodata').append(tr);

	if(sinlength>0 && isLineChart)
	{
		if(ovStats!=null && ovStats['All']!=undefined)
		{
			var hsinn = [];
			var hsinf = [];
			for (var hh=0;hh<sinlength;hh++)
			{
				hsinn.push([(hh+1), ovStats['All'][0]]);
				hsinf.push([(hh+1), ovStats['All'][1]]);
			}
			if(is90)alldat.push({label:'90% ('+ovStats['All'][0]+' ms)', data: hsinn});
			if(is50)alldat.push({label:'50% ('+ovStats['All'][1]+' ms)', data: hsinf});
		}
	}

	if(flotlinmultichart) {
		flotlinmultichart.destroy();
	}

	if(isLineChart)
	{
		flotlinmultichart = $.plot("#flot-line-chart", alldat, {
			series: {
				lines: {
					show: true
				},
				points: {
					show: true
				}
			},
            grid: {
                hoverable: true, //IMPORTANT! this is needed for tooltip to work
				canvasText: {show: true, font: "sans 8px", series: sers}
            },
			xaxis: {
				min: 0,
				mode: "categories",
				tickLength: 0

			}
		});
		$("#flot-line-chart").off("plothover").bind("plothover", function(event, pos, item) {
			if (item) {
				if ((previousLabel != item.series.label) || (previousPoint != item.dataIndex)) {
					previousPoint = item.dataIndex;
					previousLabel = item.series.label;
					$("#tooltip").remove();

					var x = item.datapoint[0];
					var y = item.datapoint[1];
					var color = item.series.color;
					
					var identi = item.series.label;
					if(eventData[item.seriesIndex]==undefined)
						return;
					if(identi!=eventData[item.seriesIndex][previousPoint].sourceFileName)
						identi += ' (' + eventData[item.seriesIndex][previousPoint].sourceFileName + ')';
					showTooltip(item.pageX, item.pageY, color,
						"<strong>"+identi+"</strong><br/><strong>" + item.series.xaxis.ticks[previousPoint].label + " - " + eventData[item.seriesIndex][previousPoint].testCaseName + "</strong><br><strong>" + y + " ms</strong>");
				}
			} else {
				$("#tooltip").remove();
				previousPoint = null;
			}
		});
	}
	else
	{
		flotlinmultichart = $.plot("#flot-line-chart", alldat, {
			series: {
				bars: {
					show: true,
					barWidth: 0.01,
					align: "center",
						order: 1
				}
			},
            grid: {
                hoverable: true, //IMPORTANT! this is needed for tooltip to work
				canvasText: {show: true, font: "sans 8px", series: sers}
            },
			xaxis: {
				min: 0,
				mode: "categories",
				tickLength: 0
			}
		});
		$("#flot-line-chart").off("plothover").bind("plothover", function(event, pos, item) {
			if (item) {
				if ((previousLabel != item.series.label) || (previousPoint != item.dataIndex)) {
					previousPoint = item.dataIndex;
					previousLabel = item.series.label;
					$("#tooltip").remove();

					var x = item.datapoint[0];
					var y = item.datapoint[1];
					var color = item.series.color;

					var identi = item.series.label;
					if(identi!=eventData[item.seriesIndex][previousPoint].sourceFileName)
						identi += ' (' + eventData[item.seriesIndex][previousPoint].sourceFileName + ')';
					showTooltip(item.pageX, item.pageY, color,
						"<strong>"+identi+"</strong><br/><strong>" + item.series.xaxis.ticks[previousPoint].label + " - " + eventData[item.seriesIndex][previousPoint].testCaseName + "</strong><br><strong>" + y + " ms</strong>");
				}
			} else {
				$("#tooltip").remove();
				previousPoint = null;
			}
		});
	}
	if(!multlinegrpwid)multlinegrpwid = $("#flot-line-chart").width();
	if(!linegrpwid)linegrpwid = $("#flot-line-chart").width();
	if(xlen>30) {
		var scale = xlen/30;
		magnify(true, 'flot-line-chart', scale, true);
	} else if(multlinegrpwid>0) {
		$("#flot-line-chart").width(multlinegrpwid);
	}	
}

function showTestcaseReportMultiLineGraph() {
	
	var alldat = [], eventData = [];
	$('#tempodata').html('');
	var tr = null;
	var xlen = 0;
	var sinlength = 0;

	var istcreports = $('#showTCSelect').is(":visible");
	var type = $('#testcasesSelect').val();
	if(istcreports)
	{
		var alldataprep = [];
		for (var i=0;i<testcaseStats.length;i++) {
			if(type=='All' || testcaseStats[i].testCaseName==type) {
				if(alldataprep[testcaseStats[i].testCaseName]==undefined)
					alldataprep[testcaseStats[i].testCaseName] = [];
				var jj = alldataprep[testcaseStats[i].testCaseName].length + 1;
				alldataprep[testcaseStats[i].testCaseName].push([jj, testcaseStats[i].executionTime]);
			}
		}
		for (var key in alldataprep) {
			if (alldataprep.hasOwnProperty(key)) {
				alldat.push({label:key, data: alldataprep[key]});
				xlen = alldataprep[key].length;
			}
		}
		sinlength = xlen;
	}
	else
	{
		return;
	}

	if(sinlength>0 && isLineChart)
	{
		if(type=='All' && ovStats!=null && ovStats['All']!=undefined)
		{
			var hsinn = [];
			var hsinf = [];
			for (var hh=0;hh<sinlength;hh++)
			{
				hsinn.push([(hh+1), ovStats['All'][0]]);
				hsinf.push([(hh+1), ovStats['All'][1]]);
			}
			if(is90)alldat.push({label:'90% ('+ovStats['All'][0]+' ms)', data: hsinn});
			if(is50)alldat.push({label:'50% ('+ovStats['All'][1]+' ms)', data: hsinf});
		}
		else
		{
			var hsinn = [];
			var hsinf = [];
			for (var hh=0;hh<sinlength;hh++)
			{
				hsinn.push([(hh+1), tcStats[type][0]]);
				hsinf.push([(hh+1), tcStats[type][1]]);
			}
			if(is90)alldat.push({label:'90% ('+tcStats[type][0]+' ms)', data: hsinn});
			if(is50)alldat.push({label:'50% ('+tcStats[type][1]+' ms)', data: hsinf});
		}
	}
	
	if(flotlinchart) {
		flotlinchart.destroy();
	}

	if(isLineChart)
	{
		flotlinchart = $.plot("#flot-line-chart", alldat, {
			series: {
				lines: {
					show: true
				},
				points: {
					show: false
				}
			},
            grid: {
                hoverable: false, //IMPORTANT! this is needed for tooltip to work
				canvasText: {show: true, font: "sans 8px"}
            },
			xaxis: {
				min: 0,
				mode: "categories",
				tickLength: 0

			}
		});
	}
	else
	{
		flotlinchart = $.plot("#flot-line-chart", alldat, {
			series: {
				bars: {
					show: true,
					barWidth: 0.01,
					align: "center",
						order: 1
				}
			},
            grid: {
                hoverable: false, //IMPORTANT! this is needed for tooltip to work
				canvasText: {show: true, font: "sans 8px"}
            },
			xaxis: {
				min: 0,
				mode: "categories",
				tickLength: 0
			}
		});
	}
	if(!multlinegrpwid)multlinegrpwid = $("#flot-line-chart").width();
	if(!linegrpwid)linegrpwid = $("#flot-line-chart").width();
	if(xlen>30) {
		var scale = xlen/30;
		magnify(true, 'flot-line-chart', scale, true);
	} else if(multlinegrpwid>0) {
		$("#flot-line-chart").width(multlinegrpwid);
	}	
}

function loadStats(value)
{
	if(isShowTableTA)
	{
		return;
	}

	if(isShowOthers)
	{
		showPieChart(value,true);
		if(undefined!=suiteStats.totalRuns && suiteStats.totalRuns>0)
			showPieChart(value,false);
			
		var currv = $('#groupFilesSelect').val();
		if((userSimulation || compareEnabled) && currv=='All')
		{
			if(multlinegrpwid)
				$("#flot-line-chart").width(multlinegrpwid);
			showMultiLineGraph();
		}
		else
		{
			if(linegrpwid)
				$("#flot-line-chart").width(linegrpwid);
			showLineGraph(value,'All');
		}
	}

	if(isShowTable)
	{
		$('#testcase-panel').show();
		$('#dtiframe').hide();
		showTable(value,'All',null);
	}
	else
	{
		if(value=="All" || endsWith(value, ".xml"))
		{
			document.getElementById('dtiframe').src = thisFile + "1.html";
		}
		else if(value.indexOf("Run-")==0)
		{
			var temp = value.substring(4);
			document.getElementById('dtiframe').src = thisFile + temp + ".html";
		}		
	}

	if(isShowTAFrame)
	{
		document.getElementById('dtiframe-ta').src = "index-ta.html";
		$('#dtiframe-ta').show();
	}
	
	if(isShowOthers)
	{
		if(compareEnabled)
		{
			var cdt = showCompareTable("all");
			showComparePieChart(cdt[0], cdt[1]);
		}

		var tot = 0, succ = 0, fail = 0, skip = 0, totTime = 0, totruns = 0, sucruns = 0, failruns = 0, concUserRuns = 0;
		if(value=='All')
		{
			tot = suiteStats.totalTestCount;
			fail = suiteStats.failedTestCount;
			skip = suiteStats.skippedTestCount;
			succ = tot - fail - skip;		
			totTime = suiteStats.executionTime;
			totruns = suiteStats.totalRuns;
			sucruns = totruns - suiteStats.failedRuns;
			failruns = suiteStats.failedRuns;
			concUserRuns = suiteStats.totalUserSuiteRuns;
		}
		else
		{
			for(var i=0;i<suiteStats.groupStats.length;i++)
			{
				if(suiteStats.groupStats[i].sourceFile==value)
				{
					tot = suiteStats.groupStats[i].totalTestCount;
					fail = suiteStats.groupStats[i].failedTestCount;
					skip = suiteStats.groupStats[i].skippedTestCount;
					succ = tot - fail - skip;		
					totTime = suiteStats.groupStats[i].executionTime;
					totruns = suiteStats.groupStats[i].totalRuns;
					sucruns = totruns - suiteStats.groupStats[i].failedRuns;
					failruns = suiteStats.groupStats[i].failedRuns;
					concUserRuns = 1;
				}
			}
		}

		$('#stats-pre-ele').html('<li>Total Testcases : '+tot+'</li> \
			<li>Concurrent User Runs : '+concUserRuns+'</li> \
			<li>Successful TestCases : '+succ+'</li> \
			<li>Failed TestCases : '+fail+'</li> \
			<li>Skipped TestCases : '+skip+'</li> \
			<li>Total ExecutionTime : '+totTime+'ms</li> \
			<li>Transactions Per Second : '+Math.floor((tot-skip)*1000/totTime)+'</li> \
			<li>Total Multiple Test Runs : '+totruns+'</li> \
			<li>Successful Multiple Test Runs : '+sucruns+'</li> \
			<li>Failed Multiple Test Runs : '+failruns+'</li>'
		   );
	}
}

function endsWith(str, suffix) {
    return str.indexOf(suffix, str.length - suffix.length) !== -1;
}

function getPieVals(reportType, isTests)
{
	if(reportType=="All")
	{
		if(isTests)
			return {total: suiteStats.totalTestCount, failed: suiteStats.failedTestCount, skipped: suiteStats.skippedTestCount};
		else
			return {total: suiteStats.totalRuns, failed: suiteStats.failedRuns};
	}
	else
	{
		for (var i=0;i<suiteStats.groupStats.length;i++) {
			if (suiteStats.groupStats[i].sourceFile==reportType) {
				if(isTests)
					return {total: suiteStats.groupStats[i].totalTestCount, failed: suiteStats.groupStats[i].failedTestCount, skipped: suiteStats.groupStats[i].skippedTestCount};
				else
					return {total: suiteStats.groupStats[i].totalRuns, failed: suiteStats.groupStats[i].failedRuns};
			}
		}
	}
}

function getLineVals(reportType, type)
{
	var reports = [];
	if(reportType=="All")
	{
		for (var i=0;i<testcaseStats.length;i++) {
			if(type=='All' || testcaseStats[i].status==type) {
				reports.push(testcaseStats[i]);
			}
		}
	}
	else
	{
		for (var i=0;i<testcaseStats.length;i++) {
			if (testcaseStats[i].identifier==reportType && (type=='All' || testcaseStats[i].status==type)) {
				reports.push(testcaseStats[i]);
			}
		}
	}
	return reports;
}

function getTableVals(reportType, type, perfType)
{
	var reports = [];				
	if(reportType=="All")
	{
		for (var key in testcaseReports) {
			if (testcaseReports.hasOwnProperty(key)) {
				for(var i=0;i<testcaseReports[key].length;i++)
				{
					if(type=='All' || testcaseReports[key][i].status==type)
						reports.push(testcaseReports[key][i]);
				}							
			}
		}
	}
	else if(reportType=="Performance")
	{
		for (var key in testcaseReports) {
			if (testcaseReports.hasOwnProperty(key) && (key==perfType || perfType=='All')) {
				for(var i=0;i<testcaseReports[key].length;i++)
				{
					if(type=='All' && testcaseReports[key][i].numberOfRuns>1)
						reports.push(testcaseReports[key][i]);
					else if(testcaseReports[key][i].numberOfRuns>1)
					{
						var errs = testcaseReports[key][i].errors;
						var hasFailed = false;
						for (var errkey in errs) {
							if (hasOwnProperty.call(errs, errkey)) hasFailed = true;
						}
						if((type=='Success' && !hasFailed) || (type=='Failed' && hasFailed) || type=='Skipped')
							reports.push(testcaseReports[key][i]);
					}
				}							
			}
		}
	}
	else
	{
		for (var key in testcaseReports) {
			if (testcaseReports.hasOwnProperty(key)) {
				if(key==reportType)
				{
					for(var i=0;i<testcaseReports[key].length;i++)
					{
						if(type=='All' || testcaseReports[key][i].status==type)
							reports.push(testcaseReports[key][i]);
					}
				}
			}
		}
	}
	return reports;
}

$(document).ready(function() {
	if(isShowOthers)
	{
		for(var i=0;i<suiteStats.groupStats.length;i++)
		{
			$('#groupFilesSelect').append('<option value="'+suiteStats.groupStats[i].sourceFile+'">'+suiteStats.groupStats[i].sourceFile+'</option>');
		}
	}
	loadStats('All');	
});

$.fn.dataTable.ext.type.order["alphanum-desc"] = function (a, b) {
	if(a.indexOf('</b>'))
		a = a.substring(0, a.indexOf('</b>'));
	if(b.indexOf('</b>'))
		b = b.substring(0, b.indexOf('</b>'));
	var aa = a.replace(/[^0-9]+/g,'').replace('.', ''), bb = b.replace(/[^0-9]+/g,'').replace('.', '');
	if(aa!='' && bb!='')
	{
		aa = parseInt(aa);
		bb = parseInt(bb);
		return aa == bb ? 0 : ( aa < bb ? 1 : -1 );
	}
	else
	{
		aa = a;
		bb = b;
		return 0;
	}    
};

jQuery.fn.dataTableExt.oSort["alphanum-asc"] = function (a, b) {
    if(a.indexOf('</b>'))
		a = a.substring(0, a.indexOf('</b>'));
	if(b.indexOf('</b>'))
		b = b.substring(0, b.indexOf('</b>'));
	var aa = a.replace(/[^0-9]+/g,'').replace('.', ''), bb = b.replace(/[^0-9]+/g,'').replace('.', '');
    if(aa!='' && bb!='')
	{
		aa = parseInt(aa);
		bb = parseInt(bb);
		return aa == bb ? 0 : ( aa < bb ? -1 : 1 );
	}
	else
	{
		aa = a;
		bb = b;
		return 0;
	}    
};

var dtdatatablechart = null;
function showCompareTable(type)
{
	var compdt = [];
	$('#compare-chart-cont').show();
	var isExists = false;
	if(dtdatatablechart)
	{
		isExists = true;
		dtdatatablechart.destroy();		
	}

	$('#dataTables-compare tbody').html('');
	if(!isExists)
	{	
		$('#dataTables-compare thead').html('');
		var thead = $('<thead>');
		$('#dataTables-compare').append(thead);
		var tr = $('<tr>');
		$('<th>Test ('+suiteStats.groupStats[0].baseUrl+')</th>').appendTo(tr);
		for(var ii=1;ii<suiteStats.groupStats.length;ii++) {
			$('<th id="thpos_'+(ii)+'">'+suiteStats.groupStats[ii].sourceFile+(' ('+suiteStats.groupStats[ii].baseUrl+')')+'</th>').appendTo(tr);					
		}
		thead.append(tr);
	}
	$('#dataTables-compare tbody').html('');
	
	var jjj = 0;

	var succ = true, fail = true;
	if(type=='Success')
	{
		succ = true;
		fail = false;
	}
	else if(type=='Failed')
	{
		succ = false;
		fail = true;
	}
	var total = 0, failed = 0;
	var compareStatsHTML = [];
	for (var key in compareStats) {
		if (compareStats.hasOwnProperty(key)) {			
			for(var ii=0;ii<compareStats[key].length;ii++) {
				var rpt = '';
				if(ii>0)rpt = ' (Repeated)';
				var posi = jjj + "" + ii;
				var tds = "<td>" + (key) + rpt + "<br/><pre id=\"run1cont-"+posi+"\" style=\"display:none\"></pre></td>"
				var isFailed = ' style="color:green"';				
				total++;
				var done = false;
				if(compareStats[key][ii].compareStatusError.indexOf('FAILED_')==0)
				{	
					if(fail)
					{
						done = true;
						failed++;
						isFailed = ' style="color:red"';
						tds += '<td id="tdpos_'+(ii+1)+'" tot="'+compareStats[key].length+'" position="'+(ii+1)+'" run1contId="#run1cont-'+posi+'" idenId="#compr-td-'+posi+'" contId="#compr-td-cont-'+posi+'" stat="'+compareStats[key][ii].compareStatusError+'" style="cursor:pointer;cursor:hand;">'+compareStats[key][ii].compareStatusError+'<b style="display:none" id="compr-td-'+posi+'" suiteKey="'+compareStats[key][ii].testSuiteKey+'">'+compareStats[key][ii].identifer+'</b><pre id="compr-td-cont-'+posi+'" style="display:none"></pre></td>';
					}
				}
				else
				{
					if(succ)
					{
						done = true;
						tds += '<td>'+compareStats[key][ii].compareStatusError + '</td>';
					}
				}
				if(done)
					compareStatsHTML[jjj++] = {data: tds, isFailed: isFailed};	
			}					
		}
	}
	for(var ii=0;ii<compareStatsHTML.length;ii++) {
		$('<tr '+compareStatsHTML[ii].isFailed+'/>').html(compareStatsHTML[ii].data).appendTo('#dataTables-compare');
		jjj++;
	}
	compareData('dataTables-compare', 'td');
	dtdatatablechart = $('#dataTables-compare').DataTable({
		"dom": 'Blfrtip',
		"buttons": [
            'copy', 'csv', 'excel', 'pdf', 'print'
        ]});
	compdt[0] = total;
	compdt[1] = failed;
	return compdt;
}

function compareData(tableID, wh) {

	var ths = $('#' + tableID + ' tbody tr '+wh);        
	var content;
	
	ths.each (function () {
		var $this = $(this);
		$this.off("click").click (
		   function() {
				var id = $(this).attr('idenId');
				var hid = $(this).attr('contId');
				var run = $(this).attr('run1contId');
				var pos = $(this).attr('position');
				var tot = $(this).attr('tot');

				/*for(var i=0;i<tot;i++) {
					if(i+1!=pos)
					{
						$('#tdpos_'+ (i+1)).hide();
						$('#thpos_'+ (i+1)).hide();
					}
				}*/
				
				if($(hid).is(":visible"))
				{
					$(hid).html('');
					$(hid).hide();
					$(run).html('');
					$(run).hide();
					/*for(var i=0;i<tot;i++) {
						$('#tdpos_'+ (i+1)).show();
						$('#thpos_'+ (i+1)).show();
					}*/
					return;
				}

				var status = $(this).attr('stat');

				var identifier = $(id).html();
				var suiteKey = $(id).attr('suiteKey');

				var suite = testcaseReports[suiteKey];
				var run1suite = testcaseReports["Run-1"];
				if(suite && run1suite)
				{
					$(hid).html('');
					$(run).html('');
					for(var i=0;i<suite.length;i++) {
						if(suite[i].testIdentifier==identifier) {
							if(status=='FAILED_STATUS_CODE')
							{
								$(hid).html(buildCompareText(suite[i]));
								$(run).html(buildCompareText(run1suite[i]));
							}
							else if(status=='FAILED_ERROR_DETAILS')
							{
								var htmlhs = buildCompareText(suite[i]);
								htmlhs += '<u>Response Error Details</u>:<br/>'+ suite[i].error;
								$(hid).html(htmlhs);
								var htmrhs = buildCompareText(run1suite[i]);
								htmrhs += '<u>Response Error Details</u>:<br/>'+ run1suite[i].error;
								$(run).html(htmrhs);
							}
							else if(status=='FAILED_ERROR_CONTENT')
							{
								var htmlhs = buildCompareText(suite[i]);
								htmlhs += '<u>Response Error Content</u>:<br/>'+ suite[i].errorText;
								$(hid).html(htmlhs);
								var htmrhs = buildCompareText(run1suite[i]);
								htmrhs += '<u>Response Error Content</u>:<br/>'+ run1suite[i].errorText;
								$(run).html(htmrhs);
							}
							else if(status=='FAILED_RESPONSE_CONTENT')
							{
								var text1 = getData(suite[i].responseContent, suite[i].responseContentType);
								var text2 = getData(run1suite[i].responseContent, run1suite[i].responseContentType);	
								var dataac = getDataAfterComparison(text1, text2);
								var htmlhs = buildCompareText(suite[i]);
								htmlhs += '<u>Response Content</u>:<br/>'+ dataac[0];
								$(hid).html(htmlhs);
								var htmrhs = buildCompareText(run1suite[i]);
								htmrhs += '<u>Response Content</u>:<br/>'+ dataac[1];
								$(run).html(htmrhs);
							}
							else if(status=='FAILED_RESPONSE_TYPE')
							{
								var htmlhs = buildCompareText(suite[i]);
								htmlhs += '<u>Response Content Type</u>:<br/>'+ suite[i].responseContentType;
								$(hid).html(htmlhs);
								var htmrhs = buildCompareText(run1suite[i]);
								htmrhs += '<u>Response Content Type</u>:<br/>'+ run1suite[i].responseContentType;
							}
							$(hid).show();
							$(run).show();
						}
					}
				}
		   }
		);
	});						
}

function buildCompareText(suite)
{
	var htm = '<u>Actual URL</u>: ' + suite.actualUrl + '<br/>';
	htm += '<u>Response Status Code</u>: '+ suite.responseStatusCode + '<br/>';
	return htm;
}

function getDataAfterComparison(lhs, rhs)
{
	var compdata = [];
	var dmp = new diff_match_patch();
	dmp.Diff_EditCost = 8;
	
	var d = dmp.diff_main(lhs, rhs);
	dmp.diff_cleanupEfficiency(d);
	var oldStr = "", newStr = "";
	for (var ii = 0, j = d.length; ii < j; ii++) {
		var arr = d[ii];
		if (arr[0] == 0) {
			oldStr += arr[1];
			newStr += arr[1];
		} else if (arr[0] == -1) {
			oldStr += "<span class='text-del'>" + arr[1] + "</span>";
		} else {
			newStr += "<span class='text-add'>" + arr[1] + "</span>";
		}
	}
	compdata[0] = oldStr;
	compdata[1] = newStr;
	return compdata;
}

function reloadCharts(val) {
	if(val=='line')
	{
		isLineChart = true;
	}
	else
	{
		isLineChart = false;		
	}
	loadStats($('#groupFilesSelect').val());
}

function DateFmt() {
  this.dateMarkers = { 
	 d:['getDate',function(v) { return ("0"+v).substr(-2,2)}], 
		 m:['getMonth',function(v) { return ("0"+v).substr(-2,2)}],
		 n:['getMonth',function(v) {
			 var mthNames = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];
			 return mthNames[v];
			 }],
		 w:['getDay',function(v) {
			 var dayNames = ["Sun","Mon","Tue","Wed","Thu","Fri","Sat"];
			 return dayNames[v];
			 }],
		 y:['getFullYear'],
		 H:['getHours',function(v) { return ("0"+v).substr(-2,2)}],
		 M:['getMinutes',function(v) { return ("0"+v).substr(-2,2)}],
		 S:['getSeconds',function(v) { return ("0"+v).substr(-2,2)}],
		 i:['toISOString',null]
  };
 
  this.format = function(date, fmt) {
	var dateMarkers = this.dateMarkers
	var dateTxt = fmt.replace(/%(.)/g, function(m, p){
	var rv = date[(dateMarkers[p])[0]]()
  
	if ( dateMarkers[p][1] != null ) rv = dateMarkers[p][1](rv)

	return rv
  });
	
  return dateTxt
  }
}

var flotpiechartcomp = null;
function showComparePieChart(total, failed) {
	$('#thirdpie').show();
	var data = [{
		label: "Success",
		data: total - failed, 
		color : 'green'
	}, {
		label: "Failed",
		data: failed, 
		color : 'red'
	}];

	if(flotpiechartcomp) {
		flotpiechartcomp.destroy();
	}
	var pieId = "#flot-pie-chart-comp";
	flotpiechartcomp = $.plot($(pieId), data, {
		series: {
			pie: {
				show: true,
				label: {
					show: true,
					radius: 1.5 / 3,
					formatter: function (label, series) {
						return '<div style="font-size:8pt;text-align:center;color:white;">' + label + '<br/>'+ series.data[0][1] + '</div>';
					},
					threshold: 0.1
				}
			}
		},
		grid: {
			hoverable: true,
			clickable: true,
			canvasText: {show: true, font: "sans 8px"}
		},
		tooltip: true,
		tooltipOpts: {
			content: "%p.0%, %s", // show percentages, rounding to 2 decimal places
			shifts: {
				x: 20,
				y: 0
			},
			defaultTheme: false
		}
	});
	$(pieId).off("plotclick").bind("plotclick", function(event, pos, obj) {
		if(!obj)return;
		showCompareTable(obj.series.label);
	});
}

var ovStats = null;
var tcStats = null;

window.addEventListener('message', function(e) {
  var eventName = e.data[0];
  var data = e.data[1];
  switch(eventName) {
    case 'setHeight':
      $('#dtiframe').css('height', (data*1+150)+'px');
      break;
	case 'setTAHeight':
      $('#dtiframe-ta').css('height', (data*1+150)+'px');
      break;
	case 'filterTestReport':
		var reportType = data.split(',')[0];
		var label = data.split(',')[1];
		showTable(reportType,label,null);
		break;
	case 'filterOtherReport':
		var reportType = data.split(',')[1];
		var label = data.split(',')[0];
		showTable(null,label,reportType);
		break;
	case 'sendRunReport':
		ovStats = JSON.parse(data);
		showOnlyLineMultiGraph();
		break;
	case 'sendTCReport':
		tcStats = JSON.parse(data);
		var tcselhtml = '<option value="All">All</option>';
		for (var key in tcStats) {
			if (tcStats.hasOwnProperty(key)) {
				tcselhtml += '<option value="'+key+'">'+key+'</option>';
			}
		}
		$('#testcasesSelect').html(tcselhtml);		
		break;
	case 'hideTAFrame':
		$('#dtiframe-ta').hide();
		break;
  }
}, false);

var is90 = true, is50 = true;

function saveSingleMultiLineGraphs()
{
	var currv = $('#groupFilesSelect').val();
	if((userSimulation || compareEnabled) && currv=='All')
	{
		Canvas2Image.saveAsPNG(flotlinmultichart.getCanvas(), $('#flot-line-chart').width(), $('#flot-line-chart').height());
	}
	else
	{
		Canvas2Image.saveAsPNG(flotlinchart.getCanvas(), $('#flot-line-chart').width(), $('#flot-line-chart').height());
	}
}

function showOnlyLineMultiGraph()
{
	is90 = $('#ninetyperchk').is(':checked');
	is50 = $('#fiftyperchk').is(':checked');
	var istcreports = $('#showTCSelect').is(":visible");
	var currv = $('#groupFilesSelect').val();
	if(istcreports)
	{
		showTestcaseReportMultiLineGraph();
	}
	else if(isShowOthers)
	{
		if((userSimulation || compareEnabled) && currv=='All')
		{
			if(multlinegrpwid)
				$("#flot-line-chart").width(multlinegrpwid);
			showMultiLineGraph();
		}
		else
		{
			if(linegrpwid)
				$("#flot-line-chart").width(linegrpwid);
			showLineGraph(currv,'All');
		}
	}
}


function showTATable()
{	
	for (var key in testcaseTAReports) {
		if (testcaseTAReports.hasOwnProperty(key)) {			
			var tds = "<td>" + key + '</td>';
			tds += "<td>" + testcaseTAReports[key][0] + '</td>';;
			tds += "<td>" + testcaseTAReports[key][1] + '</td>';
			$('<tr/>').html(tds).appendTo('#dataTables-ta');
		}
	}
	$('#dataTables-ta').DataTable({
		"dom": 'Blfrtip',
        "buttons": [
            'copy', 'csv', 'excel', 'pdf', 'print'
        ]
	});
}

function replayTest(trid, index)
{
	var tcf = $(trid).data('tcf');
	var tc = $(trid).data('tc');
	var actualRequest = JSON.stringify($(trid).data('actualRequest'));
	ajaxCall(true, "PUT", "/reports?action=replayTest&testcaseFileName="+tcf+"&testCaseName="+tc, "", actualRequest, {}, function(data){
		var content = getTestResultContent(data);
		$('#tddata-'+index).html(content);
		$('#tddata-'+index).find('[click-event]').off().on('click', function() {
			var evt = $(e.target).attr('click-event');
			execFunction1(evt);
		});
	}, null);
	return false;
}

function getIssueDetails(trid, index)
{
	var tcf = $(trid).data('tcf');
	var tc = $(trid).data('tc');
	var actualRequest = JSON.stringify($(trid).data('actualRequest'));

	ajaxCall(true, "PUT", "/reports?action=getContent&testcaseFileName=gatf-issuetracking-api-int.xml&testCaseName=targetapi&isExternalLogsApi=true", "", actualRequest, {}, function(data){
		if(data.requestContent!='')
		{
			$('<div id="popup_wrapper" style="background:#FFFFFF;-moz-box-shadow: 0 0 5px #ff0000;-webkit-box-shadow: 0 0 5px #ff0000;box-shadow: 0 0 5px #ff0000;border:2px solid #fff000;position:fixed;z-index:2;left:300px;top:100px;padding:40px;width:600px;"><center><b style="font-size:25px">Issue Details</b></center><p>URL</p><input id="popup_url" type="text" style="width:100%"/><br/><br/><p>Content</p><textarea style="width:100%;height:200px" id="popup_cont"></textarea><br/><br/><button click-event="createIssue()">Create Issue</button>&nbsp;&nbsp;<button click-event="removePw()">Close</button></div>').prependTo('body'); 
			$('#popup_url').val(data.url);
			$('#popup_cont').val(data.requestContent);
		}
		else
			alert("Issue could not be created");
	}, null);
	return false;
}

function removePw() {
  $('#popup_wrapper').remove();
  return false;
}

function createIssue()
{
	var actualReq = {url: $('#popup_url').val(), requestContent: $('#popup_cont').val()};
	ajaxCall(true, "PUT", "/reports?action=createIssue&testcaseFileName=gatf-issuetracking-api-int.xml&testCaseName=targetapi&isExternalLogsApi=true", "", JSON.stringify(actualReq), {}, function(data){
		if(data.responseStatusCode<300)
		{
			alert("Successfully created Issue");
		}
		else
			alert("Issue could not be created");
	}, null);
	$('#popup_wrapper').remove()
	return false;
}

function ajaxCall(blockUi, meth, url, contType, content, vheaders, sfunc, efunc)
{
	if(blockUi)$.blockUI({ message: '<h3><img src="resources/busy.gif" /> Just a moment...</h3>' }); 
	$.ajax({
	  headers: vheaders,
	  type: meth,
	  processData: false,
	  url: url,
	  contentType: contType,
	  data: content
	}).done(function(msg,statusText,jqXhr) {
	  if(blockUi)$.unblockUI();
	  var data = jqXhr.responseText;
	  try
	  {
		data = JSON.parse(jqXhr.responseText)
	  }
	  catch (err)
	  {
		data = jqXhr.responseText;
	  }
	  sfunc(data);
	}).fail(function(jqXhr, textStatus, msg) {
	  if(blockUi)$.unblockUI();
	  if(efunc==null)alert(jqXhr.responseText);
	  else efunc(jqXhr.responseText);
	});
}