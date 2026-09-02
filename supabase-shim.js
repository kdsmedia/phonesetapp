// ============================================================
// PHONESET -- Supabase Shim (kompatibel dengan pola Firebase)
// Panel index.html memakai db.ref(...).on/once/set/update/remove/limitToLast
// dan auth.signInWithEmailAndPassword/signUp/signOut/onAuthStateChanged.
// Shim ini menerjemahkan pola Firebase ke Supabase (REST/PostgREST+Realtime).
// ============================================================
(function () {
'use strict';

const CONFIG = {
  url: (window.SUPABASE_URL || '').replace(/\/+$/, ''),
  anonKey: (window.SUPABASE_ANON_KEY || ''),
};

if (!CONFIG.url || !CONFIG.anonKey) {
  console.warn('[PHONESET] Supabase belum dikonfigurasi. Pastikan config/supabase-config.js dimuat sebelum script ini.');
}

const sdk = window.supabase.createClient(CONFIG.url, CONFIG.anonKey, {
  auth: { persistSession: true, autoRefreshToken: true },
  realtime: { params: { eventsPerSecond: 10 } },
});

// ============================================================
// AUTH ADAPTER (Firebase-compatible)
// ============================================================
const authShim = {
  currentUser: null,
};

function toUser(sb) {
  if (!sb) return null;
  const meta = sb.user_metadata || {};
  return {
    uid: sb.id,
    email: sb.email,
    displayName: meta.name || ((sb.email || '').split('@')[0]),
    providerData: [{ providerId: ((sb.app_metadata || {})).provider || 'password' }],
    async updateProfile(profile) {
      if (profile && profile.displayName) {
        await sdk.auth.updateUser({ data: { name: profile.displayName } });
      }
    },
  };
}

function authError(e) {
  if (e && e.message) {
    const m = e.message;
    if (m.indexOf('Invalid login credentials') !== -1) {
      e.code = 'auth/invalid-credential';
    } else if (m.indexOf('already registered') !== -1) {
      e.code = 'auth/email-already-in-use';
    } else if (m.indexOf('Password should be at least') !== -1) {
      e.code = 'auth/weak-password';
    } else if (m.indexOf('rate limit') !== -1) {
      e.code = 'auth/too-many-requests';
    }
  }
  if (!e.code) {
    e.code = e.status ? 'auth/http-' + e.status : 'auth/error';
  }
  return e;
}

function runAuthCbs(user) {
  authShim.currentUser = user;
  authStateCbs.forEach(function (cb) {
    try { cb(user); } catch (err) { console.warn('[PHONESET] auth cb', err); }
  });
}

let authStateCbs = [];
let authStateSub = null;

authShim.signInWithEmailAndPassword = async function (email, pass) {
  const { data, error } = await sdk.auth.signInWithPassword({ email: email, password: pass });
  if (error) throw authError(error);
  const u = toUser(data.user);
  runAuthCbs(u);
  return { user: u };
};

authShim.createUserWithEmailAndPassword = async function (email, pass) {
  const { data, error } = await sdk.auth.signUp({ email: email, password: pass });
  if (error) throw authError(error);
  const u = toUser(data.user || (data.session && data.session.user));
  runAuthCbs(u);
  return { user: u, cred: { user: u } };
};

authShim.signOut = async function () {
  const { error } = await sdk.auth.signOut();
  if (error) console.warn('[PHONESET] signOut', error);
  runAuthCbs(null);
};

authShim.onAuthStateChanged = function (cb) {
  authStateCbs.push(cb);
  if (authStateSub) {
    try { authStateSub.data.unsubscribe(); } catch (err) {}
  }
  authStateSub = sdk.auth.onAuthStateChange(function (evt, session) {
    runAuthCbs(session && session.user ? toUser(session.user) : null);
  });
  sdk.auth.getSession().then(function (res) {
    const data = res.data || {};
    if (data.session && data.session.user) {
      runAuthCbs(toUser(data.session.user));
    }
  }).catch(function () {});
  return function () {
    try { authStateSub.data.unsubscribe(); } catch (err) {}
  };
};

// ============================================================
// SNAPSHOT & REF ADAPTER
// ============================================================
function makeSnap(val, ref) {
  const exists = val !== null && val !== undefined;
  return {
    val: function () { return val; },
    exists: function () { return exists; },
    ref: function () { return ref; },
    key: function () { return ref.key; },
  };
}

let listenerRegistry = {};

function detachListener(key) {
  const item = listenerRegistry[key];
  if (item) {
    if (item.timer) clearInterval(item.timer);
    delete listenerRegistry[key];
  }
}

function detachDataListeners() {
  Object.keys(listenerRegistry).forEach(detachListener);
}

// ============================================================
// PATH PARSER -> Supabase tables
// ============================================================
function parsePath(fullPath) {
  let segs = String(fullPath || '').split('/').filter(Boolean);
  if (segs[0] === 'phoneset') segs.shift();
  if (segs[0] === 'users') segs.shift();
  if (segs.length === 0) return { kind: 'root' };

  if (segs[0] === 'profile') return { kind: 'profile', uid: null };

  const uid = segs[0];
  if (segs[1] === 'profile') return { kind: 'profile', uid: uid };
  if (segs[1] === 'subscription') {
    return { kind: 'subscription', uid: uid, tail: segs.slice(2) };
  }
  if (segs[1] !== 'devices') return { kind: 'unknown', segs: segs };
  if (segs.length === 2) return { kind: 'devices', uid: uid };

  const did = segs[2];
  const rest = segs.slice(3);
  if (rest.length === 0) return { kind: 'device', uid: uid, did: did };

  const head = rest[0];
  const tail = rest.slice(1);
  if (head === 'info') return { kind: 'deviceInfo', uid: uid, did: did };
  if (head === 'backup_timestamp') return { kind: 'deviceBackupTs', uid: uid, did: did };
  if (head === 'commands') {
    return { kind: 'commands', uid: uid, did: did, cmdId: tail[0] || null };
  }
  return { kind: 'deviceData', uid: uid, did: did, section: head, entryId: tail.join('/') || null };
}

// ============================================================
// FETCH HELPERS
// ============================================================
async function fetchTable(table) {
  const res = await sdk.from(table).select('*');
  if (res.error) throw res.error;
  return res.data || [];
}

async function fetchOne(table, col, val) {
  const res = await sdk.from(table).select('*').eq(col, val).maybeSingle();
  if (res.error) throw res.error;
  return res.data || null;
}

function deviceToTree(r) {
  return {
    id: r.id,
    info: (r.info || {}),
    backup_timestamp: (r.backup_timestamp || 0),
    registered_at: (r.registered_at || Date.now()),
    name: (r.name || ''),
    model: (r.model || ''),
    brand: (r.brand || ''),
    android: (r.android || ''),
    battery: r.battery,
    last_seen: (r.last_seen || 0),
  };
}

function cmdToTree(r) {
  return {
    type: r.type,
    value: r.value,
    status: r.status,
    timestamp: r.timestamp,
    result: r.result,
    result_at: r.result_at,
  };
}

async function fetchValue(ref) {
  const m = parsePath(ref.path);
  switch (m.kind) {
    case 'profile': {
      const uid = m.uid || uidFromPath(ref.path);
      const row = await fetchOne('profiles', 'id', uid);
      return row ? {
        id: row.id,
        name: row.name,
        email: row.email,
        createdAt: (row.created_at || Date.now()),
        provider: (row.provider || 'email'),
      } : null;
    }
    case 'subscription': {
      const uid = m.uid || uidFromPath(ref.path);
      const row = await fetchOne('subscriptions', 'user_id', uid);
      const base = row ? {
        activeUntil: (row.active_until || 0),
        pending: (row.pending || {}),
      } : null;
      if (row && m.tail && m.tail.length > 0) {
        let cur = base;
        let i;
        for (i = 0; i < m.tail.length; i++) {
          if (cur === null || cur === undefined) break;
          cur = cur[m.tail[i]];
        }
        return (cur === undefined ? null : cur);
      }
      return base;
    }
    case 'devices': {
      const rows = await fetchTable('devices');
      const filtered = rows.filter(function (r) {
        return String(r.user_id) === String(m.uid);
      });
      const tree = {};
      filtered.forEach(function (r) {
        tree[r.id] = deviceToTree(r);
      });
      return tree;
    }
    case 'device': {
      const row = await fetchOne('devices', 'id', m.did);
      return row ? deviceToTree(row) : null;
    }
    case 'deviceInfo': {
      const row = await fetchOne('devices', 'id', m.did);
      return row ? (row.info || {}) : null;
    }
    case 'deviceBackupTs': {
      const row = await fetchOne('devices', 'id', m.did);
      return row ? (row.backup_timestamp || 0) : null;
    }
    case 'commands': {
      if (m.cmdId) {
        const row = await fetchOne('commands', 'id', m.cmdId);
        return row ? cmdToTree(row) : null;
      }
      const rows = await fetchTable('commands');
      const filtered = rows.filter(function (r) {
        return String(r.device_id) === String(m.did);
      });
      const tree = {};
      filtered.forEach(function (r) {
        tree[r.id] = cmdToTree(r);
      });
      return tree;
    }
    case 'deviceData': {
      if (m.entryId) {
        const row = await fetchOne('device_data', 'entry_id', m.entryId);
        if (row && String(row.device_id) === String(m.did) && row.section === m.section) {
          return row.data;
        }
        return null;
      }
      const rows = await fetchTable('device_data');
      const filtered = rows.filter(function (r) {
        return String(r.device_id) === String(m.did) && r.section === m.section;
      });
      filtered.sort(function (a, b) {
        return ((a.updated_at || 0)) - ((b.updated_at || 0));
      });
      const tree = {};
      filtered.forEach(function (r) {
        tree[r.entry_id] = r.data;
      });
      return tree;
    }
    case 'root': {
      const subs = await fetchTable('subscriptions');
      const profs = await fetchTable('profiles');
      const tree = {};
      (subs || []).forEach(function (s) {
        tree[s.user_id] = tree[s.user_id] || {};
        tree[s.user_id].subscription = {
          activeUntil: (s.active_until || 0),
          pending: (s.pending || {}),
        };
      });
      (profs || []).forEach(function (p) {
        tree[p.id] = tree[p.id] || {};
        tree[p.id].profile = {
          id: p.id,
          name: p.name,
          email: p.email,
          createdAt: (p.created_at || Date.now()),
          provider: (p.provider || 'email'),
        };
      });
      return tree;
    }
    default:
      return null;
  }
}

function uidFromPath(pathStr) {
  const segs = String(pathStr || '').split('/').filter(Boolean);
  let i;
  for (i = 0; i < segs.length; i++) {
    if (segs[i] === 'users' && segs[i + 1]) return segs[i + 1];
  }
  return null;
}

// ============================================================
// MUTATION ADAPTER
// ============================================================
async function mutateValue(ref, mode, val, mergeInfo) {
  const m = parsePath(ref.path);
  const uid = m.uid || uidFromPath(ref.path);
  switch (m.kind) {
    case 'profile': {
      if (mode === 'remove') {
        await sdk.from('profiles').delete().eq('id', uid);
      } else {
        const body = {
          id: uid,
          name: (val && val.name) || null,
          email: (val && val.email) || null,
          created_at: (val && val.createdAt) || Date.now(),
          provider: (val && val.provider) || 'email',
        };
        await sdk.from('profiles').upsert(body, { onConflict: 'id' });
      }
      break;
    }
    case 'subscription': {
      if (m.tail && m.tail.length > 0) {
        const row = await fetchOne('subscriptions', 'user_id', uid);
        const next = { ...(row || {}) };
        const col = m.tail[0];
        if (col === 'pending') {
          next.pending = (mode === 'remove'
            ? {}
            : (val || {}));
        } else if (col === 'activeUntil' || col === 'active_until') {
          next.active_until = (mode === 'remove'
            ? 0
            : (val || 0));
        }
        await sdk.from('subscriptions').upsert({
          user_id: uid,
          active_until: (next.active_until || next.activeUntil || 0),
          pending: (next.pending || {}),
        }, { onConflict: 'user_id' });
      } else if (mode === 'remove') {
        await sdk.from('subscriptions').delete().eq('user_id', uid);
      } else {
        const cur = await fetchOne('subscriptions', 'user_id', uid);
        const merged = { ...(cur || {}), ...(val || {}) };
        await sdk.from('subscriptions').upsert({
          user_id: uid,
          active_until: (merged.active_until !== undefined ? merged.active_until : (merged.activeUntil || 0)),
          pending: (merged.pending || {}),
        }, { onConflict: 'user_id' });
      }
      break;
    }
    case 'devices': {
      if (mode === 'remove') {
        const rows = await fetchTable('devices');
        const mine = rows.filter(function (r) {
          return String(r.user_id) === String(uid);
        });
        let i;
        for (i = 0; i < mine.length; i++) {
          await sdk.from('devices').delete().eq('id', mine[i].id);
        }
      }
      break;
    }
    case 'device': {
      if (mode === 'remove') {
        await sdk.from('devices').delete().eq('id', m.did);
      }
      break;
    }
    case 'deviceInfo': {
      if (mode === 'remove') {
        await sdk.from('devices').delete().eq('id', m.did);
      } else {
        const row = await fetchOne('devices', 'id', m.did);
        const cur = row || {};
        const info = mergeInfo ? { ...(cur.info || {}), ...(val || {}) } : (val || {});
        const body = {
          id: m.did,
          user_id: uid,
          name: (info.name || cur.name || ''),
          model: (info.model || cur.model || ''),
          brand: (info.brand || cur.brand || ''),
          android: (info.android || cur.android || ''),
          battery: (info.battery !== undefined ? info.battery : cur.battery),
          last_seen: (info.lastSeen !== undefined ? info.lastSeen : cur.last_seen),
          info: info,
          backup_timestamp: (cur.backup_timestamp || 0),
          registered_at: (cur.registered_at || Date.now()),
        };
        await sdk.from('devices').upsert(body, { onConflict: 'id' });
      }
      break;
    }
    case 'deviceBackupTs': {
      const row = await fetchOne('devices', 'id', m.did);
      const cur = row || {};
      await sdk.from('devices').upsert({
        id: m.did,
        user_id: uid,
        info: (cur.info || {}),
        backup_timestamp: (val || 0),
        registered_at: (cur.registered_at || Date.now()),
      }, { onConflict: 'id' });
      break;
    }
    case 'commands': {
      if (m.cmdId) {
        if (mode === 'remove') {
          await sdk.from('commands').delete().eq('id', m.cmdId);
        } else {
          const cur = await fetchOne('commands', 'id', m.cmdId);
          const merged = { ...(cur || {}), ...(val || {}) };
          await sdk.from('commands').upsert({
            id: m.cmdId,
            user_id: uid,
            device_id: m.did,
            type: (merged.type || ''),
            value: (merged.value || null),
            status: (merged.status || 'pending'),
            timestamp: (merged.timestamp || Date.now()),
            result: (merged.result || null),
            result_at: (merged.result_at || null),
          }, { onConflict: 'id' });
        }
      }
      break;
    }
    case 'deviceData': {
      if (m.entryId) {
        if (mode === 'remove') {
          await sdk.from('device_data').delete().eq('device_id', m.did).eq('section', m.section).eq('entry_id', m.entryId);
        } else {
          const row = await fetchOne('device_data', 'entry_id', m.entryId);
          let data;
          if (mergeInfo && row && String(row.device_id) === String(m.did) && row.section === m.section) {
            data = { ...(row.data || {}), ...(val || {}) };
          } else {
            data = (val || {});
          }
          await sdk.from('device_data').upsert({
            device_id: m.did,
            section: m.section,
            entry_id: m.entryId,
            data: data,
            updated_at: Date.now(),
          }, { onConflict: 'device_id,section,entry_id' });
        }
      } else {
        const rows = await fetchTable('device_data');
        const mine = rows.filter(function (r) {
          return String(r.device_id) === String(m.did) && r.section === m.section;
        });
        let i;
        for (i = 0; i < mine.length; i++) {
          await sdk.from('device_data').delete().eq('entry_id', mine[i].entry_id);
        }
      }
      break;
    }
    default:
      throw new Error('SupabaseShim: mutasi tak dikenal: ' + ref.path);
  }
}

// ============================================================
// REF OBJECT
// ============================================================
function makeRef(pathStr) {
  const ref = {
    path: (pathStr || ''),
    key: (pathStr || '').split('/').filter(Boolean).pop() || null,
    _limit: null,
    _timer: null,
    _last: null,
  };

  ref.child = function (p) {
    return makeRef((ref.path.replace(/\/+$/, '') + '/' + String(p).replace(/^\/+/, '')));
  };

  ref.limitToLast = function (n) {
    ref._limit = n;
    return ref;
  };

  ref.key = function () {
    return ref.keyVal;
  };

  ref.ref = function () {
    return ref;
  };

  ref.on = function (evt, cb) {
    if (evt !== 'value') return ref;
    const poll = async function () {
      try {
        const v = await fetchValue(ref);
        if (v === undefined) return;
        const changed = JSON.stringify(v) !== JSON.stringify(ref._last);
        ref._last = v;
        if (changed || ref._forceFirst) {
          ref._forceFirst = false;
          cb(makeSnap(v, ref));
        }
      } catch (err) {
        console.error('[PHONESET] listen', ref.path, err);
      }
    };
    ref._forceFirst = true;
    detachListener(ref.path);
    poll();
    ref._timer = setInterval(poll, 4000);
    listenerRegistry[ref.path] = { timer: ref._timer };
    return ref;
  };

  ref.once = function (evt, cb) {
    if (evt !== 'value') return ref;
    fetchValue(ref).then(function (v) {
      cb(makeSnap(v, ref));
    }).catch(function (err) {
      console.error('[PHONESET] once', ref.path, err);
      cb(makeSnap(null, ref));
    });
    return ref;
  };

  ref.off = function (evt) {
    if (evt && evt !== 'value') return ref;
    detachListener(ref.path);
    return ref;
  };

  ref.set = async function (val) {
    await mutateValue(ref, 'set', val, false);
  };

  ref.update = async function (val) {
    await mutateValue(ref, 'update', val, true);
  };

  ref.remove = async function () {
    await mutateValue(ref, 'remove', null, false);
  };

  ref.push = async function (val) {
    const id = Date.now() + '_' + Math.random().toString(36).slice(2, 8);
    const child = makeRef(ref.path + '/' + id);
    if (val !== undefined) {
      await child.set(val);
    }
    return child;
  };

  return ref;
}

function makeDb() {
  const db = {
    ref: function (pathStr) {
      return makeRef(pathStr || '');
    },
  };
  return db;
}

// ============================================================
// STORAGE (kosong -- upload media via perangkat / object storage publik)
// ============================================================
const storageShim = null;

// ============================================================
// EKSPOR GLOBAL (API Firebase-compatible)
// ============================================================
window.db = makeDb();
window.auth = authShim;
window.storage = storageShim;
window.supabaseDb = sdk;
window.attachDataListener = function (key, ref, cb) {
  detachListener(key);
  ref.on('value', function (snap) {
    cb(snap);
  });
  listenerRegistry[key] = listenerRegistry[ref.path];
};
window.detachDataListeners = detachDataListeners;
})();