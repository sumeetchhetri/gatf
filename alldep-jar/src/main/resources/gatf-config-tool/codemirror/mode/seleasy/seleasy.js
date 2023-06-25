// Distributed under an MIT license

// CodeMirror2 mode/seleasy/seleasy.js (text/x-seleasy) beta 0.1 (2023-07-23)

(function(mod) {
  if (typeof exports == "object" && typeof module == "object") // CommonJS
    mod(require("../../lib/codemirror"));
  else if (typeof define == "function" && define.amd) // AMD
    define(["../../lib/codemirror"], mod);
  else // Plain browser env
    mod(CodeMirror);
})(function(CodeMirror) {
"use strict";

CodeMirror.defineMode("seleasy",function(){
		//   null - magic touch
                                                        //   1 - keyword
                                                        //   2 - def
                                                        //   3 - atom
                                                        //   4 - operator
                                                        //   5 - variable-2 (predefined)
                                                        //   [x,y] - x=1,2,3; y=must be defined if x{...}
        var SELEASY={
			"->": 4,
			"++": 4,
			"--": 4,
			"**": 4,
			"=~": 4,
			"!~": 4,
			"*": 4,
			"/": 4,
			"%": 4,
			"x": 4,
			"+": 4,
			".": 4,
			"<<": 4,
			">>": 4,
			"<": 4,
			">": 4,
			"<=": 4,
			">=": 4,
			"lt": 4,
			"gt": 4,
			"le": 4,
			"ge": 4,
			"==": 4,
			"!=": 4,
			"<=>": 4,
			"eq": 4,
			"ne": 4,
			"cmp": 4,
			"~~": 4,
			"&": 4,
			"|": 4,
			"^": 4,
			"&&": 4,
			"||": 4,
			"//": 4,
			"..": 4,
			"...": 4,
			"??": 1,
			"??-": 1,
			"??+": 1,
			":?": 1,
			"?": 4,
			":": 4,
			"=": 4,
			"+=": 4,
			"-=": 4,
			"*=": 4,
			",": 4,
			"=>": 4,
			"::": 4,
			"not": 4,
			"and": 4,
			"or": 4,
			"xor": 4,
			"if": [1, 1],
			"elsif": [1, 1],
			"else": [1, 1],
			"while": [1, 1],
			"unless": [1, 1],
			"for": [1, 1],
			"break": 1,
			"close": 1,
			"continue": [1, 1],
			"eval": 1,
			"exec": 1,
			"exit": 1,
			"goto": 1,
			"import": 1,
			"index": 1,
			"int": 1,
			"join": 1,
			"open": 1,
			"require": 1,
			"sleep": 1,
			"substr": 1,
			"subtest": 1,
			"@call": 1,
			"@print": 3,
			"@driver": 3,
			"@window": 3,
			"@element": 3,
			"@sc": 3,
			"@printprovjson": 3,
			"@printProv": 3,
			"@index": 3,
			"@line": 3,
			"@cntxtparam": 3,
			"pass": 1,
			"fail": 1,
			"warn": 1,
			"dynprops": 1,
			"maximize": 1,
			"include": 1,
			"config": 1,
			"xpath": 3,
			"css": 3,
			"id": 3,
			"name": 3,
			"class": 3,
			"tag": 3,
			"cssselector": 3,
			"text": 3,
			"partiallinktext": 3,
			"linktext": 3,
			"jq": 3,
			"jquery": 3,
			"active": 3,
			"this": 3,
			"current": 3,
			"var": 1,
			"robot": 1,
			"type": 3,
			"click": 3,
			"back": 1,
			"forward": 1,
			"refresh": 1,
			"clear": 3,
			"submit": 3,
			"setting": 1,
			"window": 1,
			"frame": 1,
			"tab": 1,
			"jsvar": 1,
			"execjs": 1,
			"screenshot": 1,
			"ele-screenshot": 1,
			"hoverclick": 3,
			"actions": 1,
			"chord": 3,
			"provider": 1,
			"transient-provider": 1,
			"confirm": 1,
			"alert": 1,
			"moveby": 3,
			"doubleclick": 3,
			"dblclick": 3,
			"netapix": 1,
			"wopensave": 1,
			"sendKeys": 3,
			"moveto": 3,
			"clickhold": 3,
			"release": 3,
			"keyup": 3,
			"keydown": 3,
			"api": 1,
			"plugin": 1,
			"jsonwrite": 3,
			"xmlwrite": 3,
			"zoom": 1,
			"pinch": 1,
			"tap": 1,
			"swipe": 1,
			"rotate": 1,
			"hidekeypad": 1,
			"shake": 1,
			"chrome": 3,
			"chrome-dkr": 3,
			"chrome-hdl": 3,
			"chrome-rec": 3,
			"firefox": 3,
			"opera": 3,
			"safari": 3,
			"ie": 3,
			"edge": 3,
			"readfile": 1,
			"mode": 1,
			"hover": 3,
			"upload": 3,
			"randomize": 3,
			"func": 1,
			"relative": 3,
			"sql": 1,
			"dsq": 1,
			"file": 1,
			"curl": 1,
			"scroll": 1,
			"ifnot": 1,
			"loop": 1,
			"over": 1,
			"till": 1,
			"visible": 3,
			"attr": 3,
			"fuzzyn": 3,
			"fuzzya": 3,
			"fuzzyauc": 3,
			"fuzzyalc": 3,
			"fuzzyan": 3,
			"fuzzyanuc": 3,
			"fuzzyanlc": 3,
			"title": 3,
			"selected": 3,
			"above": 3,
			"below": 3,
			"leftof": 3,
			"rightof": 3,
			"near": 3,
			"true": 3,
			"false": 3,
			"filevar": 1,
			"others": 3,
			"on": 3,
			"off": 3,
			"post": 3,
			"get": 3,
			"put": 3,
			"delete": 3,
			"tagname": 3,
			"width": 3,
			"height": 3,
			"posx": 3,
			"posy": 3,
			"main": 3,
			"cssvalue": 3,
			"lazy": 3,
			"jsonread": 3,
			"jsonpath": 3,
			"xmlread": 3,
			"xmlpath": 3,
			"mongo": 1,
			"counter": 1,
			"ds": 1,
			"query": 1,
			"timeout": 1,
			"waitready": 1,
			"scrollup": 1,
			"scrollpageup": 1,
			"scrollpagedown": 1,
			"scrolldown": 1,
			"execjsfile": 1,
			"canvas": 1,
			"touch": 1,
			"variable": 1,
			"layer": 3,
			"normal": 3,
			"integration": 3,
			"parent": 3,
			"clk_focus": 3,
			"capability_set": 1,
			"currenturl": 3,
			"pagesource": 3,
			"xpos": 3,
			"ypos": 3,
			"alerttext": 3,
			"alphanumeric": 3,
			"alpha": 3,
			"alphanumericlc": 3,
			"alphalc": 3,
			"alphanumericuc": 3,
			"alphauc": 3,
			"numeric": 3,
			"value": 3,
			"range": 3,
			"prefixed": 3,
			"prefixed_": 3,
			"status": 3,
			"header": 3,
			"json": 3,
			"select": 3,
			"clickandhold": 3,
			"contextclick": 3,
			"rightclick": 3,
			"movetoelement": 3,
			"draganddrop": 3,
			"dragdrop": 3,
			"dragdrop1": 3,
			"draganddrop1": 3,
			"movebyoffset": 3,
			"ok": 3,
			"yes": 3,
			"cancel": 3,
			"no": 3,
			"enabled": 3,
			"className": 3
		};

        var RXstyle="string-2";
        var RXmodifiers=/[goseximacplud]/;              // NOTE: "m", "s", "y" and "tr" need to correct real modifiers for each regexp type

		function tokenComment(stream, state) {
		    var maybeEnd = false, ch;
		    while (ch = stream.next()) {
		      if (ch == "/" && maybeEnd) {
		        state.tokenize = tokenSeleasy;
		        break;
		      }
		      maybeEnd = (ch == "*");
		    }
		    return ret("comment", "comment");
		  }
		
		var type, content;
		function ret(tp, style, cont) {
		   type = tp; content = cont;
		   return style;
		}

        function tokenChain(stream,state,chain,style,tail){     // NOTE: chain.length > 2 is not working now (it's for s[...][...]geos;)
                state.chain=null;                               //                                                          12   3tail
                state.style=null;
                state.tail=null;
                state.tokenize=function(stream,state){
                        var e=false,c,i=0;
                        while(c=stream.next()){
                                if(c===chain[i]&&!e){
                                        if(chain[++i]!==undefined){
                                                state.chain=chain[i];
                                                state.style=style;
                                                state.tail=tail;}
                                        else if(tail)
                                                stream.eatWhile(tail);
                                        state.tokenize=tokenSeleasy;
                                        return style;}
                                e=!e&&c=="\\";}
                        return style;};
                return state.tokenize(stream,state);}

        function tokenSOMETHING(stream,state,string){
                state.tokenize=function(stream,state){
                        if(stream.string==string)
                                state.tokenize=tokenSeleasy;
                        stream.skipToEnd();
                        return "string";};
                return state.tokenize(stream,state);}

        function tokenSeleasy(stream,state){
                if(stream.eatSpace()) {
						return null;
                }
                if(stream.string.substring(stream.start).startsWith('{')) {
					stream.pos = stream.start + 1;
					return 'operator';
				}
                if(stream.string.substring(stream.start).startsWith('}')) {
					stream.pos = stream.start + 1;
					return 'operator';
				}
                if(stream.string.substring(stream.start).startsWith('#j')) {
					stream.pos = stream.start + 2;
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('chrome-')) {
					stream.pos = stream.string.substring(stream.start).indexOf(" ")!=-1?stream.string.substring(stream.start).indexOf(" "):stream.string.length;
					return 'atom';
				}
                if(stream.string.substring(stream.start).startsWith('<<<(java)')) {
					stream.pos = stream.start + 9;
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('<<<(js)')) {
					stream.pos = stream.start + 7;
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('<<<(python)')) {
					stream.pos = stream.start + 11;
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('<<<(ruby)')) {
					stream.pos = stream.start + 9;
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('<<<(groovy)')) {
					stream.pos = stream.start + 11;
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('<<<')) {
					stream.pos = stream.start + 3;
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('begin code in java')) {
					stream.pos = stream.start + 18;
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('begin code in js')) {
					stream.pos = stream.start + 16;
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('begin code in groovy')) {
					stream.pos = stream.start + 20;
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('begin code in python')) {
					stream.pos = stream.start + 20;
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('begin code in ruby')) {
					stream.pos = stream.start + 18;
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('end code')) {
					stream.pos = stream.start + 8;
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('wait for')) {
					stream.pos = stream.start + 8;
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('wait till visible')) {
					stream.pos = stream.start + 17;
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('>>>')) {
					stream.pos = stream.start + 3;
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('ele-screenshot')) {
					stream.pos = stream.start + stream.string.substring(stream.start).indexOf(" ");
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('browser-scope')) {
					stream.pos = stream.start + stream.string.substring(stream.start).indexOf(" ");
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('session-scope')) {
					stream.pos = stream.start + stream.string.substring(stream.start).indexOf(" ");
					return 'keyword';
				}
				if(stream.string.substring(stream.start).startsWith('??-')) {
					stream.pos = stream.start + stream.string.substring(stream.start).indexOf(" ");
					return 'keyword';
				}
				if(stream.string.substring(stream.start).startsWith('??+')) {
					stream.pos = stream.start + stream.string.substring(stream.start).indexOf(" ");
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('??')) {
					stream.pos = stream.start + stream.string.substring(stream.start).indexOf(" ");
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('?!')) {
					stream.pos = stream.start + stream.string.substring(stream.start).indexOf(" ");
					return 'keyword';
				}
				if(stream.string.substring(stream.start).startsWith(':?!')) {
					stream.pos = stream.start + stream.string.substring(stream.start).indexOf(" ");
					return 'keyword';
				}
				if(stream.string.substring(stream.start).startsWith(':?')) {
					stream.pos = stream.start + stream.string.substring(stream.start).indexOf(" ");
					return 'keyword';
				}
                if(stream.string.substring(stream.start).startsWith('#')) {
					stream.pos = stream.start + stream.string.substring(stream.start).indexOf(" ");
					return 'keyword';
				}
                if(state.chain)
                        return tokenChain(stream,state,state.chain,state.style,state.tail);
                if(stream.match(/^(\-?((\d[\d_]*)?\.\d+(e[+-]?\d+)?|\d+\.\d*)|0x[\da-fA-F_]+|0b[01_]+|\d[\d_]*(e[+-]?\d+)?)/))
                        return 'number';
                if(stream.match(/^<<(?=[_a-zA-Z])/)){                  // NOTE: <<SOMETHING\n...\nSOMETHING\n
                        stream.eatWhile(/\w/);
                        return tokenSOMETHING(stream,state,stream.current().substr(2));}
                if(stream.sol()&&stream.match(/^\=item(?!\w)/)){// NOTE: \n=item...\n=cut\n
                        return tokenSOMETHING(stream,state,'=cut');}
                /*if(stream.match(/^goto\s+(['"]*)(.*)(['"]*)/)){  
					   return 'keyword';
				}*/
                var ch=stream.next();
                if(ch=='"'||ch=="'"){                           // NOTE: ' or " or <<'SOMETHING'\n...\nSOMETHING\n or <<"SOMETHING"\n...\nSOMETHING\n
                        if(prefix(stream, 3)=="<<"+ch){
                                var p=stream.pos;
                                stream.eatWhile(/\w/);
                                var n=stream.current().substr(1);
                                if(n&&stream.eat(ch))
                                        return tokenSOMETHING(stream,state,n);
                                stream.pos=p;}
                        return tokenChain(stream,state,[ch],"string");}
                if(ch=="`"){
                        return tokenChain(stream,state,[ch],"variable-2");}
                if(ch=="/" && stream.string.substring(stream.start).startsWith("/*")){
						stream.pos = 1;
						if (stream.eat("*")) {
					      state.tokenize = tokenComment;
					      return tokenComment(stream, state);
					    } else if (stream.eat("/")) {
					      stream.skipToEnd();
					      return ret("comment", "comment");
					    }
                        /*if(look(stream, 0)=="/"){
                                stream.skipToEnd();
                                return "comment";}
                        else if(look(stream, 0)=="*"){
                                stream.eat("/");
                                stream.skipToEnd();
                                return "comment";}
                        else if(!/~\s*$/.test(prefix(stream)))
                                return "operator";
                        else
                                return tokenChain(stream,state,[ch],RXstyle,RXmodifiers);}*/
                }
                if(ch=="/" && stream.string.substring(stream.start).startsWith("//")){
					stream.skipToEnd();
                    return "comment";
				}
                if(ch=="$"){
                		let v = stream.string.substring(stream.start).match(/\$\{[a-zA-Z_0-9]+\}/);
                		if(v && v.length>0 && v[0]) {
                			stream.pos += v[0].length-1;
                			return "variable-2";
                		}
                        else
                                return 'atom'}
                if(/[@%]/.test(ch)){
                        var p=stream.pos;
                        if(stream.eat("^")&&stream.eat(/[A-Z]/)||!/[@$%&]/.test(look(stream, -2))&&stream.eat(/[=|\\\-#?@;:&`~\^!\[\]*'"$+.,\/<>()]/)){
                                var c=stream.current();
                                if(SELEASY[c.toLowerCase()])
                                        return "variable-2";}
                        stream.pos=p;}
                /*if(ch=="#"){
                        if(look(stream, -2)!="$"){
                                stream.skipToEnd();
                                return "comment";}}*/
                if(/[:+\-\^*$&%@=<>!?|\/~\.]/.test(ch)){
                        var p=stream.pos;
                        stream.eatWhile(/[:+\-\^*$&%@=<>!?|\/~\.]/);
                        if(SELEASY[stream.current().toLowerCase()])
                                return "operator";
                        else
                                stream.pos=p;}
                if(ch=="_"){
                        if(stream.pos==1){
                                if(suffix(stream, 6)=="_END__"){
                                        return tokenChain(stream,state,['\0'],"comment");}
                                else if(suffix(stream, 7)=="_DATA__"){
                                        return tokenChain(stream,state,['\0'],"variable-2");}
                                else if(suffix(stream, 7)=="_C__"){
                                        return tokenChain(stream,state,['\0'],"string");}}}
                if(/\w/.test(ch)){
                        var p=stream.pos;
                        if(look(stream, -2)=="{"&&(look(stream, 0)=="}"||stream.eatWhile(/\w/)&&look(stream, 0)=="}"))
                                return "string";
                        else
                                stream.pos=p;}
                if(ch=='@'){
                        var p=stream.pos;
                        stream.eatWhile(/[a-zA-Z_]/);
                        if(/[\da-z]/.test(look(stream, 0))){
                                stream.pos=p;}
                        else{
                                var c=SELEASY[stream.current().toLowerCase()];
                                if(!c)
                                        return "string";
                                if(c[1])
                                        c=c[0];
                                if(c==1)
	                                    return "keyword";
	                            else if(c==2)
	                                    return "def";
	                            else if(c==3)
	                                    return "atom";
	                            else if(c==4)
	                                    return "operator";
	                            else if(c==5)
	                                    return "variable-2";
	                            else
	                                    return "string";
                                        }}
                if(/[a-zA-Z]/.test(ch)){
                        var l=look(stream, -2);
                        var p=stream.pos;
                        stream.eatWhile(/[a-zA-Z_]/);
                        if(/[\da-z]/.test(look(stream, 0))){
                                stream.pos=p;}
                        else{
                                var c=SELEASY[stream.current().toLowerCase()];
                                if(!c)
                                        return "string";
                                if(c[1])
                                        c=c[0];
                                if(l!=":"){
                                        if(c==1)
                                                return "keyword";
                                        else if(c==2)
                                                return "def";
                                        else if(c==3)
                                                return "atom";
                                        else if(c==4)
                                                return "operator";
                                        else if(c==5)
                                                return "variable-2";
                                        else
                                                return "meta";}
                                else
                                        return "string";}}
                if(/[a-zA-Z_]/.test(ch)){
                        var l=look(stream, -2);
                        stream.eatWhile(/\w/);
                        var c=SELEASY[stream.current().toLowerCase()];
                        if(!c)
                                return "string";
                        if(c[1])
                                c=c[0];
                        if(l!=":"){
                                if(c==1)
                                        return "keyword";
                                else if(c==2)
                                        return "def";
                                else if(c==3)
                                        return "atom";
                                else if(c==4)
                                        return "operator";
                                else if(c==5)
                                        return "variable-2";
                                else
                                        return "meta";}
                        else
                                return "string";}
                return "string";}

        return {
            startState: function() {
                return {
                    tokenize: tokenSeleasy,
                    chain: null,
                    style: null,
                    tail: null
                };
            },
            token: function(stream, state) {
                return (state.tokenize || tokenSeleasy)(stream, state);
            },
		    blockCommentStart: "/*",
		    blockCommentEnd: "*/",
		    blockCommentContinue: " * ",
		    closeBrackets: "()[]{}''\"\"",
            lineComment: '//',
            fold: "brace"
        };
});

CodeMirror.registerHelper("wordChars", "seleasy", /[\w$]/);

CodeMirror.defineMIME("text/x-seleasy", "seleasy");

// it's like "peek", but need for look-ahead or look-behind if index < 0
function look(stream, c){
  return stream.string.charAt(stream.pos+(c||0));
}

// return a part of prefix of current stream from current position
function prefix(stream, c){
  if(c){
    var x=stream.pos-c;
    return stream.string.substr((x>=0?x:0),c);}
  else{
    return stream.string.substr(0,stream.pos-1);
  }
}

// return a part of suffix of current stream from current position
function suffix(stream, c){
  var y=stream.string.length;
  var x=y-stream.pos+1;
  return stream.string.substr(stream.pos,(c&&c<y?c:x));
}

// eating and vomiting a part of stream from current position
function eatSuffix(stream, c){
  var x=stream.pos+c;
  var y;
  if(x<=0)
    stream.pos=0;
  else if(x>=(y=stream.string.length-1))
    stream.pos=y;
  else
    stream.pos=x;
}

});
