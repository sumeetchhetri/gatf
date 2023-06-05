if (window.jQuery) {
} else {
   	(function(){"use strict";var C=document,M=window,st=C.documentElement,L=C.createElement.bind(C),ft=L("div"),q=L("table"),Mt=L("tbody"),ot=L("tr"),H=Array.isArray,S=Array.prototype,Bt=S.concat,U=S.filter,at=S.indexOf,ct=S.map,Dt=S.push,ht=S.slice,z=S.some,_t=S.splice,Pt=/^#(?:[\w-]|\\.|[^\x00-\xa0])*$/,Ht=/^\.(?:[\w-]|\\.|[^\x00-\xa0])*$/,It=/<.+>/,$t=/^\w+$/;function J(t,n){var r=jt(n);return!t||!r&&!D(n)&&!h(n)?[]:!r&&Ht.test(t)?n.getElementsByClassName(t.slice(1).replace(/\\/g,"")):!r&&$t.test(t)?n.getElementsByTagName(t):n.querySelectorAll(t)}var dt=function(){function t(n,r){if(!!n){if(Y(n))return n;var i=n;if(g(n)){var u=(Y(r)?r[0]:r)||C;if(i=Pt.test(n)&&"getElementById"in u?u.getElementById(n.slice(1).replace(/\\/g,"")):It.test(n)?yt(n):J(n,u),!i)return}else if(A(n))return this.ready(n);(i.nodeType||i===M)&&(i=[i]),this.length=i.length;for(var s=0,f=this.length;s<f;s++)this[s]=i[s]}}return t.prototype.init=function(n,r){return new t(n,r)},t}(),e=dt.prototype,o=e.init;o.fn=o.prototype=e,e.length=0,e.splice=_t,typeof Symbol=="function"&&(e[Symbol.iterator]=S[Symbol.iterator]);function Y(t){return t instanceof dt}function B(t){return!!t&&t===t.window}function D(t){return!!t&&t.nodeType===9}function jt(t){return!!t&&t.nodeType===11}function h(t){return!!t&&t.nodeType===1}function Ft(t){return typeof t=="boolean"}function A(t){return typeof t=="function"}function g(t){return typeof t=="string"}function v(t){return t===void 0}function P(t){return t===null}function lt(t){return!isNaN(parseFloat(t))&&isFinite(t)}function G(t){if(typeof t!="object"||t===null)return!1;var n=Object.getPrototypeOf(t);return n===null||n===Object.prototype}o.isWindow=B,o.isFunction=A,o.isArray=H,o.isNumeric=lt,o.isPlainObject=G;function d(t,n,r){if(r){for(var i=t.length;i--;)if(n.call(t[i],i,t[i])===!1)return t}else if(G(t))for(var u=Object.keys(t),i=0,s=u.length;i<s;i++){var f=u[i];if(n.call(t[f],f,t[f])===!1)return t}else for(var i=0,s=t.length;i<s;i++)if(n.call(t[i],i,t[i])===!1)return t;return t}o.each=d,e.each=function(t){return d(this,t)},e.empty=function(){return this.each(function(t,n){for(;n.firstChild;)n.removeChild(n.firstChild)})};function Wt(t){return v(t)?this[0]?this[0].textContent:"":this.each(function(n,r){!h(r)||(r.textContent=t)})}e.text=Wt;function I(){for(var t=[],n=0;n<arguments.length;n++)t[n]=arguments[n];var r=Ft(t[0])?t.shift():!1,i=t.shift(),u=t.length;if(!i)return{};if(!u)return I(r,o,i);for(var s=0;s<u;s++){var f=t[s];for(var a in f)r&&(H(f[a])||G(f[a]))?((!i[a]||i[a].constructor!==f[a].constructor)&&(i[a]=new f[a].constructor),I(r,i[a],f[a])):i[a]=f[a]}return i}o.extend=I,e.extend=function(t){return I(e,t)};var qt=/\S+/g;function $(t){return g(t)?t.match(qt)||[]:[]}e.toggleClass=function(t,n){var r=$(t),i=!v(n);return this.each(function(u,s){!h(s)||d(r,function(f,a){i?n?s.classList.add(a):s.classList.remove(a):s.classList.toggle(a)})})},e.addClass=function(t){return this.toggleClass(t,!0)},e.removeAttr=function(t){var n=$(t);return this.each(function(r,i){!h(i)||d(n,function(u,s){i.removeAttribute(s)})})};function Ut(t,n){if(!!t){if(g(t)){if(arguments.length<2){if(!this[0]||!h(this[0]))return;var r=this[0].getAttribute(t);return P(r)?void 0:r}return v(n)?this:P(n)?this.removeAttr(t):this.each(function(u,s){!h(s)||s.setAttribute(t,n)})}for(var i in t)this.attr(i,t[i]);return this}}e.attr=Ut,e.removeClass=function(t){return arguments.length?this.toggleClass(t,!1):this.attr("class","")},e.hasClass=function(t){return!!t&&z.call(this,function(n){return h(n)&&n.classList.contains(t)})},e.get=function(t){return v(t)?ht.call(this):(t=Number(t),this[t<0?t+this.length:t])},e.eq=function(t){return o(this.get(t))},e.first=function(){return this.eq(0)},e.last=function(){return this.eq(-1)};function T(t,n,r){if(!!h(t)){var i=M.getComputedStyle(t,null);return r?i.getPropertyValue(n)||void 0:i[n]||t.style[n]}}function E(t,n){return parseInt(T(t,n),10)||0}function gt(t,n){return E(t,"border".concat(n?"Left":"Top","Width"))+E(t,"padding".concat(n?"Left":"Top"))+E(t,"padding".concat(n?"Right":"Bottom"))+E(t,"border".concat(n?"Right":"Bottom","Width"))}var X={};function zt(t){if(X[t])return X[t];var n=L(t);C.body.insertBefore(n,null);var r=T(n,"display");return C.body.removeChild(n),X[t]=r!=="none"?r:"block"}function vt(t){return T(t,"display")==="none"}function pt(t,n){var r=t&&(t.matches||t.webkitMatchesSelector||t.msMatchesSelector);return!!r&&!!n&&r.call(t,n)}function j(t){return g(t)?function(n,r){return pt(r,t)}:A(t)?t:Y(t)?function(n,r){return t.is(r)}:t?function(n,r){return r===t}:function(){return!1}}e.filter=function(t){var n=j(t);return o(U.call(this,function(r,i){return n.call(r,i,r)}))};function x(t,n){return n?t.filter(n):t}e.detach=function(t){return x(this,t).each(function(n,r){r.parentNode&&r.parentNode.removeChild(r)}),this};var Jt=/^\s*<(\w+)[^>]*>/,Yt=/^<(\w+)\s*\/?>(?:<\/\1>)?$/,mt={"*":ft,tr:Mt,td:ot,th:ot,thead:q,tbody:q,tfoot:q};function yt(t){if(!g(t))return[];if(Yt.test(t))return[L(RegExp.$1)];var n=Jt.test(t)&&RegExp.$1,r=mt[n]||mt["*"];return r.innerHTML=t,o(r.childNodes).detach().get()}o.parseHTML=yt,e.has=function(t){var n=g(t)?function(r,i){return J(t,i).length}:function(r,i){return i.contains(t)};return this.filter(n)},e.not=function(t){var n=j(t);return this.filter(function(r,i){return(!g(t)||h(i))&&!n.call(i,r,i)})};function R(t,n,r,i){for(var u=[],s=A(n),f=i&&j(i),a=0,y=t.length;a<y;a++)if(s){var c=n(t[a]);c.length&&Dt.apply(u,c)}else for(var p=t[a][n];p!=null&&!(i&&f(-1,p));)u.push(p),p=r?p[n]:null;return u}function bt(t){return t.multiple&&t.options?R(U.call(t.options,function(n){return n.selected&&!n.disabled&&!n.parentNode.disabled}),"value"):t.value||""}function Gt(t){return arguments.length?this.each(function(n,r){var i=r.multiple&&r.options;if(i||Ot.test(r.type)){var u=H(t)?ct.call(t,String):P(t)?[]:[String(t)];i?d(r.options,function(s,f){f.selected=u.indexOf(f.value)>=0},!0):r.checked=u.indexOf(r.value)>=0}else r.value=v(t)||P(t)?"":t}):this[0]&&bt(this[0])}e.val=Gt,e.is=function(t){var n=j(t);return z.call(this,function(r,i){return n.call(r,i,r)})},o.guid=1;function w(t){return t.length>1?U.call(t,function(n,r,i){return at.call(i,n)===r}):t}o.unique=w,e.add=function(t,n){return o(w(this.get().concat(o(t,n).get())))},e.children=function(t){return x(o(w(R(this,function(n){return n.children}))),t)},e.parent=function(t){return x(o(w(R(this,"parentNode"))),t)},e.index=function(t){var n=t?o(t)[0]:this[0],r=t?this:o(n).parent().children();return at.call(r,n)},e.closest=function(t){var n=this.filter(t);if(n.length)return n;var r=this.parent();return r.length?r.closest(t):n},e.siblings=function(t){return x(o(w(R(this,function(n){return o(n).parent().children().not(n)}))),t)},e.find=function(t){return o(w(R(this,function(n){return J(t,n)})))};var Xt=/^\s*<!(?:\[CDATA\[|--)|(?:\]\]|--)>\s*$/g,Kt=/^$|^module$|\/(java|ecma)script/i,Qt=["type","src","nonce","noModule"];function Vt(t,n){var r=o(t);r.filter("script").add(r.find("script")).each(function(i,u){if(Kt.test(u.type)&&st.contains(u)){var s=L("script");s.text=u.textContent.replace(Xt,""),d(Qt,function(f,a){u[a]&&(s[a]=u[a])}),n.head.insertBefore(s,null),n.head.removeChild(s)}})}function Zt(t,n,r,i,u){i?t.insertBefore(n,r?t.firstChild:null):t.nodeName==="HTML"?t.parentNode.replaceChild(n,t):t.parentNode.insertBefore(n,r?t:t.nextSibling),u&&Vt(n,t.ownerDocument)}function N(t,n,r,i,u,s,f,a){return d(t,function(y,c){d(o(c),function(p,O){d(o(n),function(b,W){var rt=r?O:W,it=r?W:O,m=r?p:b;Zt(rt,m?it.cloneNode(!0):it,i,u,!m)},a)},f)},s),n}e.after=function(){return N(arguments,this,!1,!1,!1,!0,!0)},e.append=function(){return N(arguments,this,!1,!1,!0)};function kt(t){if(!arguments.length)return this[0]&&this[0].innerHTML;if(v(t))return this;var n=/<script[\s>]/.test(t);return this.each(function(r,i){!h(i)||(n?o(i).empty().append(t):i.innerHTML=t)})}e.html=kt,e.appendTo=function(t){return N(arguments,this,!0,!1,!0)},e.wrapInner=function(t){return this.each(function(n,r){var i=o(r),u=i.contents();u.length?u.wrapAll(t):i.append(t)})},e.before=function(){return N(arguments,this,!1,!0)},e.wrapAll=function(t){for(var n=o(t),r=n[0];r.children.length;)r=r.firstElementChild;return this.first().before(n),this.appendTo(r)},e.wrap=function(t){return this.each(function(n,r){var i=o(t)[0];o(r).wrapAll(n?i.cloneNode(!0):i)})},e.insertAfter=function(t){return N(arguments,this,!0,!1,!1,!1,!1,!0)},e.insertBefore=function(t){return N(arguments,this,!0,!0)},e.prepend=function(){return N(arguments,this,!1,!0,!0,!0,!0)},e.prependTo=function(t){return N(arguments,this,!0,!0,!0,!1,!1,!0)},e.contents=function(){return o(w(R(this,function(t){return t.tagName==="IFRAME"?[t.contentDocument]:t.tagName==="TEMPLATE"?t.content.childNodes:t.childNodes})))},e.next=function(t,n,r){return x(o(w(R(this,"nextElementSibling",n,r))),t)},e.nextAll=function(t){return this.next(t,!0)},e.nextUntil=function(t,n){return this.next(n,!0,t)},e.parents=function(t,n){return x(o(w(R(this,"parentElement",!0,n))),t)},e.parentsUntil=function(t,n){return this.parents(n,t)},e.prev=function(t,n,r){return x(o(w(R(this,"previousElementSibling",n,r))),t)},e.prevAll=function(t){return this.prev(t,!0)},e.prevUntil=function(t,n){return this.prev(n,!0,t)},e.map=function(t){return o(Bt.apply([],ct.call(this,function(n,r){return t.call(n,r,n)})))},e.clone=function(){return this.map(function(t,n){return n.cloneNode(!0)})},e.offsetParent=function(){return this.map(function(t,n){for(var r=n.offsetParent;r&&T(r,"position")==="static";)r=r.offsetParent;return r||st})},e.slice=function(t,n){return o(ht.call(this,t,n))};var tn=/-([a-z])/g;function K(t){return t.replace(tn,function(n,r){return r.toUpperCase()})}e.ready=function(t){var n=function(){return setTimeout(t,0,o)};return C.readyState!=="loading"?n():C.addEventListener("DOMContentLoaded",n),this},e.unwrap=function(){return this.parent().each(function(t,n){if(n.tagName!=="BODY"){var r=o(n);r.replaceWith(r.children())}}),this},e.offset=function(){var t=this[0];if(!!t){var n=t.getBoundingClientRect();return{top:n.top+M.pageYOffset,left:n.left+M.pageXOffset}}},e.position=function(){var t=this[0];if(!!t){var n=T(t,"position")==="fixed",r=n?t.getBoundingClientRect():this.offset();if(!n){for(var i=t.ownerDocument,u=t.offsetParent||i.documentElement;(u===i.body||u===i.documentElement)&&T(u,"position")==="static";)u=u.parentNode;if(u!==t&&h(u)){var s=o(u).offset();r.top-=s.top+E(u,"borderTopWidth"),r.left-=s.left+E(u,"borderLeftWidth")}}return{top:r.top-E(t,"marginTop"),left:r.left-E(t,"marginLeft")}}};var Et={class:"className",contenteditable:"contentEditable",for:"htmlFor",readonly:"readOnly",maxlength:"maxLength",tabindex:"tabIndex",colspan:"colSpan",rowspan:"rowSpan",usemap:"useMap"};e.prop=function(t,n){if(!!t){if(g(t))return t=Et[t]||t,arguments.length<2?this[0]&&this[0][t]:this.each(function(i,u){u[t]=n});for(var r in t)this.prop(r,t[r]);return this}},e.removeProp=function(t){return this.each(function(n,r){delete r[Et[t]||t]})};var nn=/^--/;function Q(t){return nn.test(t)}var V={},rn=ft.style,en=["webkit","moz","ms"];function un(t,n){if(n===void 0&&(n=Q(t)),n)return t;if(!V[t]){var r=K(t),i="".concat(r[0].toUpperCase()).concat(r.slice(1)),u="".concat(r," ").concat(en.join("".concat(i," "))).concat(i).split(" ");d(u,function(s,f){if(f in rn)return V[t]=f,!1})}return V[t]}var sn={animationIterationCount:!0,columnCount:!0,flexGrow:!0,flexShrink:!0,fontWeight:!0,gridArea:!0,gridColumn:!0,gridColumnEnd:!0,gridColumnStart:!0,gridRow:!0,gridRowEnd:!0,gridRowStart:!0,lineHeight:!0,opacity:!0,order:!0,orphans:!0,widows:!0,zIndex:!0};function wt(t,n,r){return r===void 0&&(r=Q(t)),!r&&!sn[t]&&lt(n)?"".concat(n,"px"):n}function fn(t,n){if(g(t)){var r=Q(t);return t=un(t,r),arguments.length<2?this[0]&&T(this[0],t,r):t?(n=wt(t,n,r),this.each(function(u,s){!h(s)||(r?s.style.setProperty(t,n):s.style[t]=n)})):this}for(var i in t)this.css(i,t[i]);return this}e.css=fn;function Ct(t,n){try{return t(n)}catch{return n}}var on=/^\s+|\s+$/;function St(t,n){var r=t.dataset[n]||t.dataset[K(n)];return on.test(r)?r:Ct(JSON.parse,r)}function an(t,n,r){r=Ct(JSON.stringify,r),t.dataset[K(n)]=r}function cn(t,n){if(!t){if(!this[0])return;var r={};for(var i in this[0].dataset)r[i]=St(this[0],i);return r}if(g(t))return arguments.length<2?this[0]&&St(this[0],t):v(n)?this:this.each(function(u,s){an(s,t,n)});for(var i in t)this.data(i,t[i]);return this}e.data=cn;function Tt(t,n){var r=t.documentElement;return Math.max(t.body["scroll".concat(n)],r["scroll".concat(n)],t.body["offset".concat(n)],r["offset".concat(n)],r["client".concat(n)])}d([!0,!1],function(t,n){d(["Width","Height"],function(r,i){var u="".concat(n?"outer":"inner").concat(i);e[u]=function(s){if(!!this[0])return B(this[0])?n?this[0]["inner".concat(i)]:this[0].document.documentElement["client".concat(i)]:D(this[0])?Tt(this[0],i):this[0]["".concat(n?"offset":"client").concat(i)]+(s&&n?E(this[0],"margin".concat(r?"Top":"Left"))+E(this[0],"margin".concat(r?"Bottom":"Right")):0)}})}),d(["Width","Height"],function(t,n){var r=n.toLowerCase();e[r]=function(i){if(!this[0])return v(i)?void 0:this;if(!arguments.length)return B(this[0])?this[0].document.documentElement["client".concat(n)]:D(this[0])?Tt(this[0],n):this[0].getBoundingClientRect()[r]-gt(this[0],!t);var u=parseInt(i,10);return this.each(function(s,f){if(!!h(f)){var a=T(f,"boxSizing");f.style[r]=wt(r,u+(a==="border-box"?gt(f,!t):0))}})}});var Rt="___cd";e.toggle=function(t){return this.each(function(n,r){if(!!h(r)){var i=v(t)?vt(r):t;i?(r.style.display=r[Rt]||"",vt(r)&&(r.style.display=zt(r.tagName))):(r[Rt]=T(r,"display"),r.style.display="none")}})},e.hide=function(){return this.toggle(!1)},e.show=function(){return this.toggle(!0)};var xt="___ce",Z=".",k={focus:"focusin",blur:"focusout"},Nt={mouseenter:"mouseover",mouseleave:"mouseout"},hn=/^(mouse|pointer|contextmenu|drag|drop|click|dblclick)/i;function tt(t){return Nt[t]||k[t]||t}function nt(t){var n=t.split(Z);return[n[0],n.slice(1).sort()]}e.trigger=function(t,n){if(g(t)){var r=nt(t),i=r[0],u=r[1],s=tt(i);if(!s)return this;var f=hn.test(s)?"MouseEvents":"HTMLEvents";t=C.createEvent(f),t.initEvent(s,!0,!0),t.namespace=u.join(Z),t.___ot=i}t.___td=n;var a=t.___ot in k;return this.each(function(y,c){a&&A(c[t.___ot])&&(c["___i".concat(t.type)]=!0,c[t.___ot](),c["___i".concat(t.type)]=!1),c.dispatchEvent(t)})};function Lt(t){return t[xt]=t[xt]||{}}function dn(t,n,r,i,u){var s=Lt(t);s[n]=s[n]||[],s[n].push([r,i,u]),t.addEventListener(n,u)}function At(t,n){return!n||!z.call(n,function(r){return t.indexOf(r)<0})}function F(t,n,r,i,u){var s=Lt(t);if(n)s[n]&&(s[n]=s[n].filter(function(f){var a=f[0],y=f[1],c=f[2];if(u&&c.guid!==u.guid||!At(a,r)||i&&i!==y)return!0;t.removeEventListener(n,c)}));else for(n in s)F(t,n,r,i,u)}e.off=function(t,n,r){var i=this;if(v(t))this.each(function(s,f){!h(f)&&!D(f)&&!B(f)||F(f)});else if(g(t))A(n)&&(r=n,n=""),d($(t),function(s,f){var a=nt(f),y=a[0],c=a[1],p=tt(y);i.each(function(O,b){!h(b)&&!D(b)&&!B(b)||F(b,p,c,n,r)})});else for(var u in t)this.off(u,t[u]);return this},e.remove=function(t){return x(this,t).detach().off(),this},e.replaceWith=function(t){return this.before(t).remove()},e.replaceAll=function(t){return o(t).replaceWith(this),this};function ln(t,n,r,i,u){var s=this;if(!g(t)){for(var f in t)this.on(f,n,r,t[f],u);return this}return g(n)||(v(n)||P(n)?n="":v(r)?(r=n,n=""):(i=r,r=n,n="")),A(i)||(i=r,r=void 0),i?(d($(t),function(a,y){var c=nt(y),p=c[0],O=c[1],b=tt(p),W=p in Nt,rt=p in k;!b||s.each(function(it,m){if(!(!h(m)&&!D(m)&&!B(m))){var et=function(l){if(l.target["___i".concat(l.type)])return l.stopImmediatePropagation();if(!(l.namespace&&!At(O,l.namespace.split(Z)))&&!(!n&&(rt&&(l.target!==m||l.___ot===b)||W&&l.relatedTarget&&m.contains(l.relatedTarget)))){var ut=m;if(n){for(var _=l.target;!pt(_,n);)if(_===m||(_=_.parentNode,!_))return;ut=_}Object.defineProperty(l,"currentTarget",{configurable:!0,get:function(){return ut}}),Object.defineProperty(l,"delegateTarget",{configurable:!0,get:function(){return m}}),Object.defineProperty(l,"data",{configurable:!0,get:function(){return r}});var bn=i.call(ut,l,l.___td);u&&F(m,b,O,n,et),bn===!1&&(l.preventDefault(),l.stopPropagation())}};et.guid=i.guid=i.guid||o.guid++,dn(m,b,O,n,et)}})}),this):this}e.on=ln;function gn(t,n,r,i){return this.on(t,n,r,i,!0)}e.one=gn;var vn=/%20/g,pn=/\r?\n/g;function mn(t,n){return"&".concat(encodeURIComponent(t),"=").concat(encodeURIComponent(n.replace(pn,`\r`)).replace(vn,"+"))}var yn=/file|reset|submit|button|image/i,Ot=/radio|checkbox/i;e.serialize=function(){var t="";return this.each(function(n,r){d(r.elements||[r],function(i,u){if(!(u.disabled||!u.name||u.tagName==="FIELDSET"||yn.test(u.type)||Ot.test(u.type)&&!u.checked)){var s=bt(u);if(!v(s)){var f=H(s)?s:[s];d(f,function(a,y){t+=mn(u.name,y)})}}})}),t.slice(1)},typeof exports<"u"?module.exports=o:M.cash=M.$=o})();
	window.$ = $;
	window.jQuery = $;
}

window.GatfUtil = new function() {
	this.__owo__ = window.open,
	this.__wostn__ = 1,
	this.__wosjp__ = [],
	this.__wosjpr__ = 1,
	this.wpensaveInit = function(optionalOpenNums) {
		window.GatfUtil.__wostn__ = optionalOpenNums;
		window.GatfUtil.__wosjp__ = [];
		window.GatfUtil.__wosjpr__ = optionalOpenNums;
		window.open = function(a, b, c) {
			window.GatfUtil.__wosjp__.push([a,b,c]);
			window.GatfUtil.__owo__(a,b,c);
			console.log(a);
		};
	},
	this.wopensaveFetch = function(openpos) {
		if(openpos<window.GatfUtil.__wosjp__.length) {
			window.GatfUtil.__wosjpr__ = window.GatfUtil.__wosjpr__ - 1;
			if(window.GatfUtil.__wosjpr__==0) {
				window.open = window.GatfUtil.__owo__;
			}
			return window.GatfUtil.__wosjp__[openpos][0];
		}
		else 
			return 'FAIL';
	},
	this.getXpath = function(node) {
	    var xpath = [];
	    if(node.attr("id"))return '//*[@id="'+node.attr("id")+'"]';
	    var pars = node.parents();
	    for(var i=0;i<pars.length;i++) {
	            if($(pars[i]).attr("id")) {
	                    xpath.push('*[@id="'+$(pars[i]).attr("id")+'"]');
	                    break;
	            } else if(i+1<pars.length && $(pars[i+1]).children($(pars[i]).prop('tagName')).length>1) {
	                    var chl = $(pars[i+1]).children($(pars[i]).prop('tagName'));
	                    var ind = -1;
	                    for(ind=0;ind<chl.length;ind++) {
	                            if($(chl[ind])[0]==$(pars[i])[0]) {
	                                    break;
	                            }
	                    }
	                    xpath.push($(pars[i]).prop('tagName')+'['+(ind+1)+']');
	            } else {
	                    xpath.push($(pars[i]).prop('tagName'));
	            }
	    }
	    if(node.parent() && node.parent().children(node.prop('tagName')).length>1) {
	            var chl = node.parent().children(node.prop('tagName'));
	            var ind = -1;
	            for(ind=0;ind<chl.length;ind++) {
	                    if($(chl[ind])[0]==node[0]) {
	                            break;
	                    }
	            }
	            xpath.unshift(node.prop('tagName')+'['+(ind+1)+']');
	    } else {
	            xpath.unshift(node.prop('tagName'));
	    }
	    xpath.reverse();
	    return "//" + xpath.join("/");
	},
	this.getCssSelectorFromXpath = function(xpath) {
	    return xpath
	            .replace(/\[(\d+?)\]/g, function(s,m1){ return '['+(m1)+']'; })
	            .replace(/\/{2}/g, '')
	            .replace(/\/+/g, ' > ')
	            .replace(/@/g, '')
	            .replace(/\[(\d+)\]/g, ':nth-of-type($1)')
	            .replace(/^\s+/, '');
	},
	this.getCssSelector = function(node) {
	    return this.getXpath(node)
	            .replace(/\[(\d+?)\]/g, function(s,m1){ return '['+(m1)+']'; })
	            .replace(/\/{2}/g, '')
	            .replace(/\/+/g, ' > ')
	            .replace(/@/g, '')
	            .replace(/\[(\d+)\]/g, ':nth-of-type($1)')
	            .replace(/^\s+/, '');
	},
	
	this.find = function(expr) {
		return $(expr).get();
	},
	
	this.findByXpath = function(expr, cntxt) {
		if(typeof $x === 'function')
			return $x(expr);
		
		const result = [];
		const nodesSnapshot = document.evaluate(expr, cntxt || document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
		for (let i=0 ; i < nodesSnapshot.snapshotLength; i++) {
			result.push(nodesSnapshot.snapshotItem(i));
		}
		return result;
	},
	
	this.check = function() {
		return "Success";	
	}
	return this;
};
