(() => {
  const body = document.body;
  const feed = document.getElementById('feed');
  const footer = document.querySelector('footer');
  const stage = document.createElement('div');
  stage.className = 'vy-stage';
  stage.innerHTML = `
    <div class="vy-topline"><span>◈ <b>VYRON SYSTEM</b></span><span id="vy-time">--:--</span><span id="vy-device">SYSTEM LINK</span></div>
    <div class="vy-panel-title" id="vy-title">SYSTEM CORE</div>
    <div class="vy-hero" id="vy-hero">
      <div class="vy-side-actions left"><button type="button" data-shortcut="whatsapp" title="WhatsApp">▤<small>WA</small></button><button type="button" data-shortcut="photo" title="Send a photo">◎<small>PHOTO</small></button></div>
      <div class="vy-reactor"><div class="vy-arc"></div><div class="vy-arc inner"></div><div class="vy-orb cpu"><small>CPU</small><span id="vy-cpu">—</span></div><div class="vy-core"><small>DISK FREE</small><span class="value" id="vy-disk">—</span><span class="unit" id="vy-disk-unit">GB</span></div><div class="vy-orb batt"><small>BATTERY</small><span id="vy-battery">—</span></div></div>
      <div class="vy-side-actions right"><button type="button" data-shortcut="mail" title="Email">✉<small>MAIL</small></button><button type="button" data-shortcut="dial" title="Dial">▦<small>DIAL</small></button></div>
    </div>
    <div class="vy-meters" id="vy-meters"><span>MEM <b id="vy-mem">—</b></span><span>NET <b id="vy-net">—</b></span><span>UP <b id="vy-up">—</b></span></div>
    <section class="vy-screen vy-overview" id="vy-overview"><h2>LIVE SYSTEM</h2><div class="row"><span>Processor</span><strong id="vy-overview-cpu">—</strong></div><div class="row"><span>Memory</span><strong id="vy-overview-mem">—</strong></div><div class="row"><span>Free storage</span><strong id="vy-overview-disk">—</strong></div><div class="row"><span>Battery</span><strong id="vy-overview-batt">—</strong></div><div class="row"><span>Network</span><strong id="vy-overview-net">—</strong></div><p class="sub" id="vy-overview-host">Waiting for Mac data…</p><div class="row"><button data-view="chat">ASK VYRON</button><button data-view="weather">WEATHER</button></div></section>
    <section class="vy-screen" id="vy-weather"><h2>WEATHER</h2><p class="sub">Local conditions from your chosen location</p><p class="vy-weather-number" id="vy-temperature">—</p><p id="vy-weather-description">Enable location or enter coordinates.</p><p id="vy-weather-details" class="sub"></p><div class="row"><button id="vy-use-location">USE CURRENT LOCATION</button></div><div class="row"><input id="vy-lat" inputmode="decimal" placeholder="Latitude"><input id="vy-lon" inputmode="decimal" placeholder="Longitude"><button id="vy-weather-load">LOAD</button></div></section>
    <section class="vy-screen" id="vy-calendar"><h2>CALENDAR</h2><p id="vy-month" class="sub"></p><div class="vy-calendar-grid" id="vy-days"></div><h2 style="margin-top:14px">YOUR EVENTS</h2><div id="vy-events"></div><div class="row"><input type="date" id="vy-event-date"><input id="vy-event-text" placeholder="Add event"><button id="vy-event-add">ADD</button></div></section>
    <section class="vy-screen" id="vy-music"><h2>MEDIA</h2><p class="sub">Audio files shared with VYRON</p><div id="vy-tracks"></div><audio id="vy-player" controls style="width:100%;margin-top:12px"></audio><button id="vy-audio-upload">ADD AUDIO</button></section>
    <section class="vy-screen" id="vy-news"><h2>NEWS TODAY</h2><p class="sub">Latest headlines with source and publication time</p><div id="vy-headlines">Loading headlines…</div></section>
    <section class="vy-screen" id="vy-apps"><h2>QUICK ACTIONS</h2><div class="row"><button data-shortcut="chat">◈ ASK VYRON</button><button data-shortcut="files">▣ FILES</button></div><div class="row"><button data-shortcut="photo">◎ PHOTO</button><button data-shortcut="mail">✉ MAIL</button></div><div class="row"><button data-shortcut="whatsapp">▤ WHATSAPP</button><button data-shortcut="dial">▦ DIAL</button></div><p class="sub">Actions open the relevant app on this device when available.</p></section>
    <section class="vy-screen" id="vy-settings"><h2>YOUR NAME</h2><p class="sub">VYRON uses it naturally in greetings and when topics change.</p><div class="row"><input id="vy-user-name" maxlength="80" placeholder="Your preferred name (optional)" autocomplete="name"><button id="vy-save-name">SAVE NAME</button></div><p id="vy-name-status" class="sub"></p><h2>CONNECTION</h2><p id="vy-connection-detail">Checking VYRON…</p><div class="row"><button id="vy-reconnect">REFRESH DATA</button><button id="vy-revoke">REVOKE PAIRED DEVICES</button></div><p class="sub">The Gemini key stays on the Mac. Phone access requires a paired VYRON session.</p></section>
  `;
  body.insertBefore(stage, feed);
  stage.appendChild(feed);
  const chatHeading = document.createElement('h2');
  chatHeading.textContent = 'CONVERSATION';
  feed.prepend(chatHeading);
  const nav = document.createElement('nav');
  nav.className = 'vy-nav';
  nav.setAttribute('aria-label', 'Jump to dashboard section');
  nav.innerHTML = '<button data-view="home" aria-label="System core">◎<small>CORE</small></button><button data-view="weather" aria-label="Weather">☼<small>WEATHER</small></button><button data-view="calendar" aria-label="Calendar">▦<small>CALENDAR</small></button><button data-view="music" aria-label="Media">♫<small>MEDIA</small></button><button data-view="news" aria-label="News">▤<small>NEWS</small></button><button data-view="chat" aria-label="Conversation">◈<small>CHAT</small></button><button data-view="apps" aria-label="Apps">⊞<small>APPS</small></button><button data-view="settings" aria-label="Settings">⚙<small>SETTINGS</small></button>';
  body.insertBefore(nav, stage);
  const ticker = document.createElement('div');
  ticker.className = 'vy-ticker';
  ticker.id = 'vy-ticker';
  ticker.textContent = 'VYRON · Waiting for live system data';
  body.insertBefore(ticker, footer);
  const $ = id => document.getElementById(id);
  let current = 'home', latest = null, lastWeather = null;
  const sectionTargets = {home:'vy-hero',chat:'feed',weather:'vy-weather',calendar:'vy-calendar',music:'vy-music',news:'vy-news',apps:'vy-apps',settings:'vy-settings'};
  function view(name, scroll = true) {
    current = name;
    nav.querySelectorAll('button').forEach(b => b.classList.toggle('active', b.dataset.view === name));
    if (name === 'calendar') drawCalendar();
    if (name === 'music') loadTracks();
    if (name === 'news') loadNews();
    if (name === 'weather' && !lastWeather) loadSavedWeather();
    const target = $(sectionTargets[name]);
    if (scroll && target) target.scrollIntoView({behavior:'smooth', block:'start'});
  }
  document.addEventListener('click', e => { const b = e.target.closest('button[data-view]'); if (b) view(b.dataset.view); });
  document.querySelectorAll('[data-shortcut]').forEach(b=>b.addEventListener('click',()=>{
    const action=b.dataset.shortcut;
    if(action==='chat'){view('chat');$('inp').focus()}
    else if(action==='photo'||action==='files') $('file-inp').click();
    else if(action==='mail') location.href='mailto:';
    else if(action==='dial') location.href='tel:';
    else if(action==='whatsapp') window.open('https://wa.me/','_blank','noopener');
  }));
  $('vy-audio-upload').addEventListener('click',()=>$('file-inp').click());
  async function system(){
    try{
      const resp=await _authFetch('/api/system'); if(!resp.ok)throw Error('Session expired');
      const d=await resp.json();latest=d;
      $('vy-cpu').textContent=d.cpu_percent==null?'N/A':Math.round(d.cpu_percent)+'%';
      $('vy-disk').textContent=d.disk_free_gb==null?'N/A':Math.round(d.disk_free_gb);
      $('vy-disk-unit').textContent=d.disk_free_gb==null?'':'GB';
      $('vy-battery').textContent=d.battery_percent==null?'N/A':Math.round(d.battery_percent)+'%';
      $('vy-mem').textContent=d.memory_percent==null?'N/A':Math.round(d.memory_percent)+'%';
      $('vy-net').textContent=d.network_mbps==null?'N/A':d.network_mbps.toFixed(1)+' MB/s';
      $('vy-up').textContent=d.uptime_seconds==null?'N/A':Math.floor(d.uptime_seconds/3600)+'h';
      $('vy-device').textContent=d.hostname||'SYSTEM LINK';
      $('vy-connection-detail').textContent=`Connected to ${d.hostname||'VYRON'} · ${d.platform||''} · ${d.process_count||0} processes`;
      $('vy-overview-cpu').textContent=$('vy-cpu').textContent;
      $('vy-overview-mem').textContent=$('vy-mem').textContent;
      $('vy-overview-disk').textContent=`${$('vy-disk').textContent} GB`;
      $('vy-overview-batt').textContent=$('vy-battery').textContent;
      $('vy-overview-net').textContent=$('vy-net').textContent;
      $('vy-overview-host').textContent=`${d.hostname||'VYRON'} · ${d.platform||''} · ${d.process_count||0} running processes`;
      ticker.textContent=`${new Date().toLocaleString()} · ${d.hostname||'VYRON'} · CPU ${$('vy-cpu').textContent} · FREE ${$('vy-disk').textContent} GB · NETWORK ${$('vy-net').textContent}${lastWeather?' · '+lastWeather:''}`;
    }catch(e){$('vy-connection-detail').textContent='Mac connection unavailable. Pair the phone again from VYRON Remote Control.';ticker.textContent='VYRON · Mac connection unavailable';}
  }
  function tick(){ $('vy-time').textContent=new Date().toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'}); }
  tick();system();setInterval(tick,1000);setInterval(system,3000);
  $('vy-reconnect').addEventListener('click',system);
  async function loadProfile(){try{const r=await _authFetch('/api/profile');if(r.ok){const p=await r.json();$('vy-user-name').value=p.user_name||''}}catch{}}
  loadProfile();
  $('vy-save-name').addEventListener('click',async()=>{const name=$('vy-user-name').value.trim();const status=$('vy-name-status');try{const r=await _authFetch('/api/profile',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({user_name:name})});status.textContent=r.ok?'Saved. VYRON will use this in the next Live conversation.':'Could not save the name.'}catch{status.textContent='Could not reach the Mac.'}});
  if(window.VyronAndroid){
    const apiButton=document.createElement('button');
    apiButton.textContent='API SOURCE';
    apiButton.addEventListener('click',()=>window.VyronAndroid.openSettings());
    $('vy-settings').querySelector('.row').appendChild(apiButton);
  }
  $('vy-revoke').addEventListener('click',async()=>{if(!confirm('Disconnect all paired phones?'))return;const r=await _authFetch('/api/revoke-devices',{method:'POST'});$('vy-connection-detail').textContent=r.ok?'Paired devices revoked.':'Could not revoke devices.'});
  async function weather(lat,lon){
    $('vy-weather-description').textContent='Loading live weather…';
    try{
      const r=await _authFetch(`/api/weather?lat=${encodeURIComponent(lat)}&lon=${encodeURIComponent(lon)}`);if(!r.ok)throw Error('Weather unavailable');
      const d=await r.json();lastWeather=`${Math.round(d.temperature_c)}°C`;
      $('vy-temperature').textContent=`${Math.round(d.temperature_c)}°C`;
      $('vy-weather-description').textContent=d.description||'Current conditions';
      $('vy-weather-details').textContent=`Wind ${d.wind_kmh??'—'} km/h · Humidity ${d.humidity_percent??'—'}% · Updated ${new Date(d.observed_at).toLocaleTimeString()}`;
      localStorage.setItem('vyron_weather_coords',JSON.stringify({lat,lon}));system();
    }catch(e){$('vy-weather-description').textContent='Weather unavailable. Check the connection and location.'}
  }
  function loadSavedWeather(){try{const c=JSON.parse(localStorage.getItem('vyron_weather_coords'));if(c){$('vy-lat').value=c.lat;$('vy-lon').value=c.lon;weather(c.lat,c.lon)}}catch{}}
  $('vy-use-location').addEventListener('click',()=>navigator.geolocation?navigator.geolocation.getCurrentPosition(p=>{const lat=p.coords.latitude,lon=p.coords.longitude;$('vy-lat').value=lat.toFixed(4);$('vy-lon').value=lon.toFixed(4);weather(lat,lon)},()=>{$('vy-weather-description').textContent='Location permission unavailable. Enter coordinates below.'}):$('vy-weather-description').textContent='Location is unsupported here. Enter coordinates below.');
  $('vy-weather-load').addEventListener('click',()=>{const lat=Number($('vy-lat').value),lon=Number($('vy-lon').value);if(Number.isFinite(lat)&&Math.abs(lat)<=90&&Number.isFinite(lon)&&Math.abs(lon)<=180)weather(lat,lon);else $('vy-weather-description').textContent='Enter valid latitude and longitude.'});
  function events(){try{return JSON.parse(localStorage.getItem('vyron_events')||'[]')}catch{return[]}}
  function drawCalendar(){
    const now=new Date(),y=now.getFullYear(),m=now.getMonth();$('vy-month').textContent=now.toLocaleString([],{month:'long',year:'numeric'}).toUpperCase();
    const grid=$('vy-days');grid.replaceChildren();for(const day of ['S','M','T','W','T','F','S']){const e=document.createElement('span');e.textContent=day;grid.append(e)}
    const start=new Date(y,m,1).getDay(),end=new Date(y,m+1,0).getDate();for(let i=0;i<start;i++)grid.append(document.createElement('span'));
    for(let day=1;day<=end;day++){const e=document.createElement('span');e.textContent=day;if(day===now.getDate())e.className='today';grid.append(e)}
    const box=$('vy-events');box.replaceChildren();const list=events().map((event,index)=>({...event,index})).sort((a,b)=>a.date.localeCompare(b.date));if(!list.length){const p=document.createElement('p');p.textContent='No events added yet.';box.append(p)}
    list.forEach((ev,i)=>{const row=document.createElement('div');row.className='row';const label=document.createElement('span');label.textContent=`${ev.date} · ${ev.text}`;const del=document.createElement('button');del.textContent='×';del.title='Remove event';del.onclick=()=>{const x=events();x.splice(ev.index,1);localStorage.setItem('vyron_events',JSON.stringify(x));drawCalendar()};row.append(label,del);box.append(row)})
  }
  $('vy-event-date').value=new Date().toLocaleDateString('en-CA');$('vy-event-add').addEventListener('click',()=>{const date=$('vy-event-date').value,text=$('vy-event-text').value.trim();if(!date||!text)return;const list=events();list.push({date,text});localStorage.setItem('vyron_events',JSON.stringify(list));$('vy-event-text').value='';drawCalendar()});
  async function loadTracks(){const box=$('vy-tracks');box.textContent='Loading your audio files…';try{const r=await _authFetch('/api/files');const d=await r.json();const files=d.files.filter(f=>/\.(mp3|m4a|wav|ogg|flac|aac)$/i.test(f.name));box.replaceChildren();if(!files.length){box.textContent='No audio files shared yet. Use ADD AUDIO.';return}for(const file of files){const row=document.createElement('div');row.className='row';const label=document.createElement('strong');label.textContent=file.name;const play=document.createElement('button');play.textContent='PLAY';play.onclick=()=>{const p=$('vy-player');p.src=`/uploads/${encodeURIComponent(file.name)}?token=${encodeURIComponent(_authToken)}`;p.play()};row.append(label,play);box.append(row)}}catch{box.textContent='Media files unavailable while the Mac is disconnected.'}}
  async function loadNews(){const box=$('vy-headlines');box.textContent='Loading live headlines…';try{const r=await _authFetch('/api/news');if(!r.ok)throw Error();const d=await r.json();box.replaceChildren();for(const story of d.stories){const row=document.createElement('div');row.className='row';const a=document.createElement('a');a.href=story.url;a.target='_blank';a.rel='noopener noreferrer';a.textContent=story.title;row.append(a);const small=document.createElement('small');small.textContent=story.published||story.source;row.append(small);box.append(row)}if(!d.stories.length)box.textContent='No current headlines.'}catch{box.textContent='News unavailable. Check the network connection.'}}
  // Every section is visible on the same canvas, so load its data at startup.
  drawCalendar();
  loadTracks();
  loadNews();
  loadSavedWeather();
  view('home', false);
})();
