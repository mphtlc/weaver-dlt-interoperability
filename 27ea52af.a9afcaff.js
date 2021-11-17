(window.webpackJsonp=window.webpackJsonp||[]).push([[10],{135:function(e,t,n){"use strict";n.d(t,"a",(function(){return u})),n.d(t,"b",(function(){return g}));var r=n(0),i=n.n(r);function a(e,t,n){return t in e?Object.defineProperty(e,t,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[t]=n,e}function o(e,t){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var r=Object.getOwnPropertySymbols(e);t&&(r=r.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),n.push.apply(n,r)}return n}function s(e){for(var t=1;t<arguments.length;t++){var n=null!=arguments[t]?arguments[t]:{};t%2?o(Object(n),!0).forEach((function(t){a(e,t,n[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):o(Object(n)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(n,t))}))}return e}function l(e,t){if(null==e)return{};var n,r,i=function(e,t){if(null==e)return{};var n,r,i={},a=Object.keys(e);for(r=0;r<a.length;r++)n=a[r],t.indexOf(n)>=0||(i[n]=e[n]);return i}(e,t);if(Object.getOwnPropertySymbols){var a=Object.getOwnPropertySymbols(e);for(r=0;r<a.length;r++)n=a[r],t.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(i[n]=e[n])}return i}var p=i.a.createContext({}),c=function(e){var t=i.a.useContext(p),n=t;return e&&(n="function"==typeof e?e(t):s(s({},t),e)),n},u=function(e){var t=c(e.components);return i.a.createElement(p.Provider,{value:t},e.children)},d={inlineCode:"code",wrapper:function(e){var t=e.children;return i.a.createElement(i.a.Fragment,{},t)}},b=i.a.forwardRef((function(e,t){var n=e.components,r=e.mdxType,a=e.originalType,o=e.parentName,p=l(e,["components","mdxType","originalType","parentName"]),u=c(n),b=r,g=u["".concat(o,".").concat(b)]||u[b]||d[b]||a;return n?i.a.createElement(g,s(s({ref:t},p),{},{components:n})):i.a.createElement(g,s({ref:t},p))}));function g(e,t){var n=arguments,r=t&&t.mdxType;if("string"==typeof e||r){var a=n.length,o=new Array(a);o[0]=b;var s={};for(var l in t)hasOwnProperty.call(t,l)&&(s[l]=t[l]);s.originalType=e,s.mdxType="string"==typeof e?e:r,o[1]=s;for(var p=2;p<a;p++)o[p]=n[p];return i.a.createElement.apply(null,o)}return i.a.createElement.apply(null,n)}b.displayName="MDXCreateElement"},185:function(e,t,n){"use strict";n.r(t),t.default=n.p+"assets/images/integration-pattern-consensus-driven-63ad28eb160267a0584ba64abbc6fa0e.jpg"},186:function(e,t,n){"use strict";n.r(t),t.default=n.p+"assets/images/integration-pattern-single-party-api-828f5c42b687e693cc9b5cf4e914e85a.jpg"},187:function(e,t,n){"use strict";n.r(t),t.default=n.p+"assets/images/integration-pattern-single-enterprise-multiple-networks-527009d72634b73ebda371b465340455.jpg"},188:function(e,t,n){"use strict";n.r(t),t.default=n.p+"assets/images/integration-pattern-single-network-multiple-cloud-cf5c86bedcefc81c781aa02bb89b2128.jpg"},77:function(e,t,n){"use strict";n.r(t),n.d(t,"frontMatter",(function(){return o})),n.d(t,"metadata",(function(){return s})),n.d(t,"toc",(function(){return l})),n.d(t,"default",(function(){return c}));var r=n(3),i=n(7),a=(n(0),n(135)),o={id:"integration-patterns",title:"Integration Patterns"},s={unversionedId:"external/what-is-interoperability/integration-patterns",id:"external/what-is-interoperability/integration-patterns",isDocsHomePage:!1,title:"Integration Patterns",description:"\x3c!--",source:"@site/docs/external/what-is-interoperability/integration-patterns.md",slug:"/external/what-is-interoperability/integration-patterns",permalink:"/weaver-dlt-interoperability/docs/external/what-is-interoperability/integration-patterns",editUrl:"https://github.com/hyperledger-labs/weaver-dlt-interoperability/edit/master/docs/external/what-is-interoperability/integration-patterns.md",version:"current",sidebar:"Documentation",previous:{title:"Levels of Interoperability",permalink:"/weaver-dlt-interoperability/docs/external/what-is-interoperability/levels-of-interoperability"},next:{title:"Interoperability Modes",permalink:"/weaver-dlt-interoperability/docs/external/interoperability-modes"}},l=[{value:"Distributed Ledger Integration Patterns",id:"distributed-ledger-integration-patterns",children:[{value:"Consensus-based integration between ledgers",id:"consensus-based-integration-between-ledgers",children:[]},{value:"Standard API integration between applications",id:"standard-api-integration-between-applications",children:[]},{value:"Single enterprise participating in multiple neworks",id:"single-enterprise-participating-in-multiple-neworks",children:[]},{value:"Single network deployed on multiple heterogenous infrastructure",id:"single-network-deployed-on-multiple-heterogenous-infrastructure",children:[]}]}],p={toc:l};function c(e){var t=e.components,o=Object(i.a)(e,["components"]);return Object(a.b)("wrapper",Object(r.a)({},p,o,{components:t,mdxType:"MDXLayout"}),Object(a.b)("p",null,"Integration patterns are well-known reusable solutions for integrating systems together. A number of patterns exist for addressing various types integration problems. The specific pattern applied in practice depends on the nature of the integration problem, the overall objective of the integration task, trade-offs in alternate approaches, and potential risks."),Object(a.b)("h2",{id:"distributed-ledger-integration-patterns"},"Distributed Ledger Integration Patterns"),Object(a.b)("p",null,"Here we present common patterns for integrating distributed ledgers. Not all problems are equal, some approaches to itegrating ledgers are preferred over others depending on the use case, the purpose of the itegration and the risks involved."),Object(a.b)("h3",{id:"consensus-based-integration-between-ledgers"},"Consensus-based integration between ledgers"),Object(a.b)("p",null,"Consensus-based integration aims to communicate the consensus view of one network to another. The consensus view is a representation of state on the ledger that is collectively agreed by the members of the network. This form of integration provides the highest assurance on the validity of state. The Weaver framework is designed to address consensus-based integration between ledgers built on different distributed ledger protocols."),Object(a.b)("p",null,Object(a.b)("img",{src:n(185).default})),Object(a.b)("h3",{id:"standard-api-integration-between-applications"},"Standard API integration between applications"),Object(a.b)("p",null,"A standard API integration relies on a single party exposing an endpoint for state exchange. The validity of state relies entirely on the trust placed on the party exposing the endpoint."),Object(a.b)("p",null,Object(a.b)("img",{src:n(186).default})),Object(a.b)("h3",{id:"single-enterprise-participating-in-multiple-neworks"},"Single enterprise participating in multiple neworks"),Object(a.b)("p",null,"A single enterprise participating in multiple networks can integrate state and contract logic across these networks using off-chain workflows. Unlike the previous pattern, this pattern relies on the enterprise having valid membership credentials on multiple networks. Significant trust must be placed on the organization coordianting the exchange of state across these networks."),Object(a.b)("p",null,Object(a.b)("img",{src:n(187).default})),Object(a.b)("h3",{id:"single-network-deployed-on-multiple-heterogenous-infrastructure"},"Single network deployed on multiple heterogenous infrastructure"),Object(a.b)("p",null,"Although not an integration pattern, this pattern demonstrates interoperability at the infrastructure layer. The ability to run nodes on multiple cloud providers, as well as on-prem infrastructure, ensures networks are resilient to failures or censorship by infrastructure providers."),Object(a.b)("p",null,Object(a.b)("img",{src:n(188).default})))}c.isMDXComponent=!0}}]);