/* Offline checks only: no packages, network, credentials, or live requests. */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const readJson = name => JSON.parse(fs.readFileSync(path.join(root, name), 'utf8'));
const collection = readJson('docs/api/testing/Trekking.postman_collection.json');
const environment = readJson('docs/api/testing/Trekking.local.postman_environment.json');
const openapi = readJson('docs/api/openapi.json');
const requests = [];
const ids = new Set();
const variables = new Set();
let compiledScripts = 0;
let jsonBodies = 0;
let assertions = 0;

function check(condition, message) {
    assert.ok(condition, message);
    assertions++;
}

assert.equal(collection.info.schema, 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json');
check(Boolean(collection.info._postman_id && collection.info.name), 'Missing collection identity');
assert.equal(environment._postman_variable_scope, 'environment');
for (const entry of environment.values) {
    check(!variables.has(entry.key), 'Duplicate environment variable: ' + entry.key);
    variables.add(entry.key);
    check(typeof entry.value === 'string' && entry.enabled === true, 'Invalid environment entry: ' + entry.key);
}

function syntheticValue(key) {
    if (key === 'baseUrl') return 'http://localhost:8080';
    if (/Id$/.test(key)) return '123';
    if (/Otp$/.test(key)) return '123456';
    if (/Email$/.test(key)) return 'qa@example.invalid';
    if (key === 'effectiveFrom') return '2026-10-21T00:00:00Z';
    return 'synthetic-' + key;
}

const synthetic = Object.fromEntries([...variables].map(key => [key, syntheticValue(key)]));
function substitute(value) {
    return value.replace(/\{\{([^{}]+)\}\}/g, (_, key) => {
        check(variables.has(key), 'Undeclared placeholder: ' + key);
        return synthetic[key];
    });
}

function inspectItems(items) {
    check(Array.isArray(items), 'Folder must contain an item array');
    for (const item of items) {
        if (item.item) { inspectItems(item.item); continue; }
        const id = item.name.split(' | ')[0];
        check(/^[A-Z]+-\d+$/.test(id), 'Invalid case ID: ' + id);
        check(!ids.has(id), 'Duplicate case ID: ' + id);
        ids.add(id);
        check(Boolean(item.request && item.request.method && item.request.url.raw), id + ': missing request essentials');
        check(item.request.url.raw.startsWith('{{baseUrl}}/api/v1/'), id + ': invalid URL');
        check(Boolean(item.request.description), id + ': missing prerequisites/description');
        check(item.protocolProfileBehavior.disableCookies === true, id + ': ambient cookies must be disabled');
        substitute(JSON.stringify(item.request));
        if (item.request.body?.mode === 'raw') {
            const raw = item.request.body.raw;
            const parsed = JSON.parse(substitute(raw));
            const visit = object => {
                for (const [key, value] of Object.entries(object)) {
                    if (/Id$/.test(key) && value !== null) check(typeof value === 'number', id + ': ' + key + ' must be a JSON number');
                    if (value && typeof value === 'object') visit(value);
                }
            };
            visit(parsed);
            jsonBodies++;
        }
        for (const kind of ['prerequest', 'test']) {
            const event = item.event.find(entry => entry.listen === kind);
            check(Boolean(event && event.script.type === 'text/javascript' && Array.isArray(event.script.exec)), id + ': missing ' + kind);
            new vm.Script(event.script.exec.join('\n'), {filename: id + '.' + kind + '.js'});
            compiledScripts++;
        }
        if (item.request.auth.type === 'noauth') {
            check(!item.request.header.some(header => header.key.toLowerCase() === 'authorization'), id + ': public request has stale bearer');
        } else {
            assert.equal(item.request.auth.type, 'bearer');
            check(item.request.auth.bearer[0].value.endsWith('Token}}'), id + ': bearer must reference a role token');
        }
        requests.push(item);
    }
}
inspectItems(collection.item);

const canonicalPath = value => value.replace(/\{\{[^{}]+\}\}/g, '{}').replace(/\{[^{}]+\}/g, '{}').replace(/\/-?\d+(?=\/|$)/g, '/{}');
const documented = new Set();
for (const [url, methods] of Object.entries(openapi.paths)) {
    for (const method of Object.keys(methods)) {
        if (['get', 'post', 'put', 'delete', 'patch', 'head', 'options'].includes(method)) documented.add(method.toUpperCase() + ' ' + canonicalPath(url));
    }
}
const covered = new Set(requests.map(item => item.request.method + ' ' + canonicalPath(item.request.url.raw.replace('{{baseUrl}}', '').split('?')[0])));
for (const operation of documented) check(covered.has(operation), 'Missing OpenAPI operation: ' + operation);
for (const operation of covered) check(documented.has(operation), 'Unknown operation in collection: ' + operation);
assert.equal(documented.size, 41, 'Expected 41 current API operations');

// Implement just the public Chai methods used by these scripts. Unsupported methods fail.
function expect(actual, negated = false) {
    let proxy;
    const verify = (condition, message) => {
        assert.ok(negated ? !condition : condition, message);
        return proxy;
    };
    proxy = new Proxy({}, {get(_, property) {
        if (['to', 'be', 'and', 'have'].includes(property)) return proxy;
        if (property === 'not') return expect(actual, !negated);
        if (property === 'empty') return verify(actual != null && Object.keys(Object(actual)).length === 0, 'Expected empty/nonempty value');
        if (property === 'equal') return expected => verify(actual === expected, 'Expected equal value');
        if (property === 'eql') return expected => verify(JSON.stringify(actual) === JSON.stringify(expected), 'Expected deeply equal value');
        if (property === 'a' || property === 'an') return type => verify(type === 'array' ? Array.isArray(actual) : type === 'object' ? actual !== null && typeof actual === 'object' && !Array.isArray(actual) : typeof actual === type, 'Expected type: ' + type);
        if (property === 'above') return expected => verify(actual > expected, 'Expected value above threshold');
        if (property === 'include') return expected => verify(actual != null && actual.includes(expected), 'Expected included value');
        if (property === 'property') return expected => verify(actual != null && Object.hasOwn(actual, expected), 'Expected property: ' + expected);
        throw new Error('Unsupported pm.expect method: ' + String(property));
    }});
    return proxy;
}

function harness(item, options = {}) {
    const values = {...synthetic, ...options.values};
    const headers = {};
    const failures = [];
    let sent = 0;
    const responseHeaders = options.headers || [];
    const pm = {
        environment: {get: key => values[key], set: (key, value) => { values[key] = value; }},
        request: {headers: {upsert: header => { headers[header.key.toLowerCase()] = header.value; }}},
        sendRequest(request, callback) {
            sent++;
            assert.equal(request.method, 'GET');
            assert.equal(request.url, 'http://localhost:8080/api/v1/auth/csrf');
            callback(null, {code: 200, json: () => ({headerName: 'X-XSRF-TOKEN', token: 'fresh-csrf'})});
        },
        expect,
        test(name, fn) { try { fn(); } catch (error) { failures.push({name, error: error.message}); } },
        response: {
            code: options.code ?? 200,
            json() { if (options.binary) throw new SyntaxError('Binary response'); return options.data ?? {}; },
            text: () => options.text ?? '',
            headers: {
                all: () => responseHeaders,
                get: key => responseHeaders.find(header => header.key.toLowerCase() === key.toLowerCase())?.value,
            },
        },
    };
    return {values, headers, failures, get sent() { return sent; }, run(kind) {
        const source = item.event.find(event => event.listen === kind).script.exec.join('\n');
        new vm.Script(source).runInNewContext({pm}, {timeout: 1000});
    }};
}

function source(item, kind = 'test') { return item.event.find(event => event.listen === kind).script.exec.join('\n'); }
function expectedStatus(item) {
    const match = source(item).match(/pm\.expect\(pm\.response\.code\)\.to\.equal\((\d+)\)/);
    check(Boolean(match), item.name + ': missing exact HTTP assertion');
    return Number(match[1]);
}

for (const item of requests) {
    const mock = harness(item);
    mock.run('prerequest');
    const pre = source(item, 'prerequest');
    if (pre.includes('pm.sendRequest')) {
        check(mock.sent === 1, item.name + ': must fetch fresh CSRF once');
        assert.equal(mock.headers['x-xsrf-token'], 'fresh-csrf');
        check(mock.headers.cookie.startsWith('XSRF-TOKEN=fresh-csrf'), item.name + ': missing CSRF cookie');
    }
    const wrong = harness(item, {code: expectedStatus(item) === 500 ? 502 : 500, data: {id: 777, accountId: 777, token: 'wrong', accessToken: 'wrong'}});
    const before = {...wrong.values};
    wrong.run('test');
    check(wrong.failures.some(failure => failure.name.includes('HTTP')), item.name + ': unexpected HTTP status must fail');
    assert.deepEqual(wrong.values, before, item.name + ': wrong HTTP response saved outputs');
}

const authRequests = requests.filter(item => source(item).includes('data.accessToken'));
check(authRequests.length >= 6, 'Missing role login/refresh scenarios');
for (const item of authRequests) {
    const match = source(item).match(/pm\.environment\.set\('([^']+)Token', data\.accessToken\)/);
    check(Boolean(match), item.name + ': missing token capture');
    const role = match[1];
    const data = {accessToken: 'exact-access-token', tokenType: 'Bearer', expiresIn: 900, account: {id: 456}};
    const mock = harness(item, {code: 200, data, headers: [{key: 'Set-Cookie', value: 'trekking_refresh=exact-refresh-token; Path=/api/v1/auth; HttpOnly; SameSite=Lax'}]});
    mock.run('prerequest');
    assert.equal(mock.values[role + 'Token'], '');
    assert.equal(mock.values[role + 'RefreshToken'], '');
    for (const other of ['customer', 'other', 'admin', 'accountant', 'guide', 'google'].filter(value => value !== role)) {
        assert.equal(mock.values[other + 'Token'], synthetic[other + 'Token'], item.name + ': login cleared another role');
    }
    if (item.request.url.raw.endsWith('/refresh')) {
        check(mock.headers.cookie.includes('trekking_refresh=' + synthetic[role + 'RefreshToken']), item.name + ': refresh must send prior cookie despite clearing outputs');
    }
    mock.run('test');
    assert.deepEqual(mock.failures, [], item.name + ': synthetic AuthResponse failed');
    assert.equal(mock.values[role + 'Token'], 'exact-access-token');
    assert.equal(mock.values[role + 'RefreshToken'], 'exact-refresh-token');
    assert.equal(mock.values[role + 'Id'], '456');
    const missingCookie = harness(item, {code: 200, data});
    missingCookie.run('test');
    check(missingCookie.failures.length > 0, item.name + ': missing refresh cookie must fail');
}

const binaryRequests = requests.filter(item => item.request.method === 'GET' && expectedStatus(item) === 200 && item.request.url.raw.endsWith('/license'));
check(binaryRequests.length >= 2, 'Missing binary license download coverage');
for (const item of binaryRequests) {
    const mock = harness(item, {code: 200, binary: true, headers: [
        {key: 'Content-Type', value: 'application/octet-stream'},
        {key: 'Content-Disposition', value: 'attachment; filename="qa.pdf"'},
        {key: 'Cache-Control', value: 'no-store'},
    ]});
    mock.run('test');
    assert.deepEqual(mock.failures, [], item.name + ': binary response must not require valid JSON');
}

console.log(`PASS: ${requests.length} cases, ${documented.size}/41 API operations, ${compiledScripts} scripts compiled, ${jsonBodies} JSON bodies, ${assertions} structural/behavior checks.`);
console.log(`PASS: fresh CSRF, role token isolation, ${authRequests.length} auth captures, refresh prior cookie, wrong-status output guards, missing-cookie detection, ${binaryRequests.length} binary responses.`);
console.log('Offline validation only; no API requests, OTP delivery, uploaded files, or live database changes were executed.');
