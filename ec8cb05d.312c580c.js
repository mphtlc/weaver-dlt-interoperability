(window.webpackJsonp=window.webpackJsonp||[]).push([[44],{112:function(e,t,r){"use strict";r.r(t),r.d(t,"frontMatter",(function(){return o})),r.d(t,"metadata",(function(){return c})),r.d(t,"toc",(function(){return s})),r.d(t,"default",(function(){return l}));var n=r(3),a=r(7),i=(r(0),r(121)),o={id:"weaver-dapps",title:"Weaver Dapps"},c={unversionedId:"external/architecture-and-design/weaver-dapps",id:"external/architecture-and-design/weaver-dapps",isDocsHomePage:!1,title:"Weaver Dapps",description:"\x3c!--",source:"@site/docs/external/architecture-and-design/weaver-dapps.md",slug:"/external/architecture-and-design/weaver-dapps",permalink:"/weaver-dlt-interoperability/docs/external/architecture-and-design/weaver-dapps",editUrl:"https://github.com/hyperledger-labs/weaver-dlt-interoperability/edit/master/docs/external/architecture-and-design/weaver-dapps.md",version:"current",sidebar:"Documentation",previous:{title:"Drivers",permalink:"/weaver-dlt-interoperability/docs/external/architecture-and-design/drivers"},next:{title:"Decentralized Identity",permalink:"/weaver-dlt-interoperability/docs/external/architecture-and-design/decentralized-identity"}},s=[{value:"Fabric",id:"fabric",children:[]},{value:"Corda",id:"corda",children:[]}],d={toc:s};function l(e){var t=e.components,o=Object(a.a)(e,["components"]);return Object(i.b)("wrapper",Object(n.a)({},d,o,{components:t,mdxType:"MDXLayout"}),Object(i.b)("p",null,"As mentioned in the ",Object(i.b)("a",{parentName:"p",href:"/weaver-dlt-interoperability/docs/external/architecture-and-design/overview"},"overview"),", DLTs that integrate with Weaver must contain an interop (IOP) module to facilitate interoperation between ledgers. The interop module contains all the logic responsible for membership, verification policies and access control policies (refer to the RFCs for more information on these). Below shows the architecture of how these interop modules work with the two currently supported DLTs, Fabric and Corda."),Object(i.b)("h2",{id:"fabric"},"Fabric"),Object(i.b)("p",null,"When Fabric is the requesting network, the IOP module is used to verify the proof and then forward the state onto the application chaincode."),Object(i.b)("p",null,Object(i.b)("img",{src:r(204).default})),Object(i.b)("p",null,"When Fabric is the responding network, the IOP module is in charge of verifying the identity of the requester, making sure the requester has access to the state they are requesting, and then finally retrieving the state from the application chaincode to send back to the requesting network."),Object(i.b)("p",null,Object(i.b)("img",{src:r(205).default})),Object(i.b)("p",null,"Verification Policy, Access Control and Membership are modular components within the interop chaincode for seperation of concerns of the code."),Object(i.b)("h2",{id:"corda"},"Corda"),Object(i.b)("p",null,"As can be seen from the diagrams below, the architecture for Corda is very similar to that of Fabric. The main difference is that the interop module and the application specific flows are in seperate CorDapps, instead of seperate chaincodes like in Fabric."),Object(i.b)("p",null,Object(i.b)("img",{src:r(206).default})),Object(i.b)("p",null,Object(i.b)("img",{src:r(207).default})))}l.isMDXComponent=!0},121:function(e,t,r){"use strict";r.d(t,"a",(function(){return p})),r.d(t,"b",(function(){return b}));var n=r(0),a=r.n(n);function i(e,t,r){return t in e?Object.defineProperty(e,t,{value:r,enumerable:!0,configurable:!0,writable:!0}):e[t]=r,e}function o(e,t){var r=Object.keys(e);if(Object.getOwnPropertySymbols){var n=Object.getOwnPropertySymbols(e);t&&(n=n.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),r.push.apply(r,n)}return r}function c(e){for(var t=1;t<arguments.length;t++){var r=null!=arguments[t]?arguments[t]:{};t%2?o(Object(r),!0).forEach((function(t){i(e,t,r[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(r)):o(Object(r)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(r,t))}))}return e}function s(e,t){if(null==e)return{};var r,n,a=function(e,t){if(null==e)return{};var r,n,a={},i=Object.keys(e);for(n=0;n<i.length;n++)r=i[n],t.indexOf(r)>=0||(a[r]=e[r]);return a}(e,t);if(Object.getOwnPropertySymbols){var i=Object.getOwnPropertySymbols(e);for(n=0;n<i.length;n++)r=i[n],t.indexOf(r)>=0||Object.prototype.propertyIsEnumerable.call(e,r)&&(a[r]=e[r])}return a}var d=a.a.createContext({}),l=function(e){var t=a.a.useContext(d),r=t;return e&&(r="function"==typeof e?e(t):c(c({},t),e)),r},p=function(e){var t=l(e.components);return a.a.createElement(d.Provider,{value:t},e.children)},u={inlineCode:"code",wrapper:function(e){var t=e.children;return a.a.createElement(a.a.Fragment,{},t)}},f=a.a.forwardRef((function(e,t){var r=e.components,n=e.mdxType,i=e.originalType,o=e.parentName,d=s(e,["components","mdxType","originalType","parentName"]),p=l(r),f=n,b=p["".concat(o,".").concat(f)]||p[f]||u[f]||i;return r?a.a.createElement(b,c(c({ref:t},d),{},{components:r})):a.a.createElement(b,c({ref:t},d))}));function b(e,t){var r=arguments,n=t&&t.mdxType;if("string"==typeof e||n){var i=r.length,o=new Array(i);o[0]=f;var c={};for(var s in t)hasOwnProperty.call(t,s)&&(c[s]=t[s]);c.originalType=e,c.mdxType="string"==typeof e?e:n,o[1]=c;for(var d=2;d<i;d++)o[d]=r[d];return a.a.createElement.apply(null,o)}return a.a.createElement.apply(null,r)}f.displayName="MDXCreateElement"},204:function(e,t,r){"use strict";r.r(t),t.default=r.p+"assets/images/fabric_dapp_flow1-2035e1e29e1d75e37636daf7863f4e83.png"},205:function(e,t,r){"use strict";r.r(t),t.default=r.p+"assets/images/fabric_dapp_flow2-cb868570a5685af68eb3df1b91c14721.png"},206:function(e,t,r){"use strict";r.r(t),t.default=r.p+"assets/images/corda_dapp_flow1-ff627af484504ac801bbb175b2940f72.png"},207:function(e,t,r){"use strict";r.r(t),t.default=r.p+"assets/images/corda_dapp_flow2-aef05d1649f2cdc62ff7f7dac5d9d746.png"}}]);