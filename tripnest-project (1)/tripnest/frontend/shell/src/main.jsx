import React from 'react';import{createRoot}from'react-dom/client';import'./style.css';
const apps=[['User UI','User accounts, authentication and profile','http://localhost:5174'],['Travel UI','Destinations and travel packages','http://localhost:5175'],['Hotel UI','Hotels and availability','http://localhost:5176'],['Transport UI','Transport options and schedules','http://localhost:5177'],['Booking UI','Trip booking and booking status','http://localhost:5178'],['Payment UI','Payment initiation and payment status','http://localhost:5179']];
function App(){return <><header><a className="brand">tripnest<span>.</span></a><strong>Frontend Shell</strong></header><main><p className="eyebrow">MICRO-FRONTEND SHELL</p><h1>One TripNest.<br/><em>Independent UIs.</em></h1><p className="lead">The shell is only the entry point. Each business domain below is a separate React application with its own build, port and deployment lifecycle.</p><div className="grid">{apps.map(([n,d,u],i)=><a className="tile" href={u} key={n}><span>0{i+1}</span><h2>{n}</h2><p>{d}</p><b>Open UI →</b></a>)}</div><section className="arch"><h2>Deployment boundary</h2><pre>Browser → Frontend Shell
           ├── user-ui      → User Service
           ├── travel-ui    → Travel Service
           ├── hotel-ui     → Hotel Service
           ├── transport-ui → Transport Service
           ├── booking-ui   → Booking Service
           └── payment-ui   → Payment Service

Each UI can be built and deployed independently.</pre></section></main></>};createRoot(document.getElementById('root')).render(<App/>);
