import {request, formatQuery, put, post, get, addHandler, removeHandler} from './request';
import {getToken} from 'credentials';

jest.mock('credentials', () => {
  return {
    getToken: jest.fn()
  };
});

const successResponse = {
  status: 200,
  content: 'I have so much content'
};
const failedResponse = {
  status: 401,
  content: 'FAILED'
};
const token = 'token-23';

global.fetch = jest.fn();
global.fetch.mockReturnValue(Promise.resolve(successResponse));

getToken.mockReturnValue(token);

const url = 'https://example.com';

describe('request', () => {
  const method = 'GET';

  beforeEach(() => {
    fetch.mockClear();
  });

  it('should open http request with given method and url', async () => {
    await request({
      url,
      method
    });

    const {method: actualMethod} = fetch.mock.calls[0][1];

    expect(fetch.mock.calls[0][0]).toBe(url);
    expect(actualMethod).toBe(method);
  });

  it('should set headers', async () => {
    const headers = {
      g: 1
    };

    await request({
      url,
      method,
      headers
    });

    const {headers: {g}} = fetch.mock.calls[0][1];

    expect(g).toBe(headers.g);
  });

  it('should set default Content-Type to application/json', async () => {
    await request({
      url,
      method
    });

    const {headers: {'Content-Type': contentType}} = fetch.mock.calls[0][1];

    expect(contentType).toBe('application/json');
  });

  it('should provide option to override Content-Type header', async () => {
    const contentType = 'text';

    await request({
      url,
      method,
      headers: {
        'Content-Type': contentType
      }
    });

    const {headers: {'Content-Type': actualContentType}} = fetch.mock.calls[0][1];

    expect(actualContentType).toBe(contentType);
  });

  it('should stringify json body objects', async () => {
    const body = {
      d: 1
    };

    await request({
      url,
      method,
      body
    });

    const {body: actualBody} = fetch.mock.calls[0][1];

    expect(actualBody).toBe(JSON.stringify(body));
  });

  it('should return successful response when status is 200', async () => {
    const response = await request({
      url,
      method
    });

    expect(response).toBe(successResponse);
  });

  it('should return rejected response when status is 401', async () => {
    fetch.mockReturnValueOnce(Promise.resolve(failedResponse));

    try {
      await request({
        url,
        method
      });
    } catch (e) {
      expect(e).toBe(failedResponse);
    }
  });

  it('should add Authorization header', async () => {
    await request({
      url,
      method
    });

    const Authorization = fetch.mock.calls[0][1].headers['X-Optimize-Authorization'];

    expect(Authorization).toBe(`Bearer ${token}`);
  });

  it('should not add Authorization header when token is empty', () => {
    getToken.mockReturnValueOnce(null);

    request({
      url,
      method
    });

    const {headers} = fetch.mock.calls[0][1];

    expect(headers).not.toBe(
      expect.objectContaining({
        'X-Optimize-Authorization': expect.any(String)
      })
    );
  });
});

describe('handlers', () => {
  it('should call a registered handler with the response', async () => {
    const spy = jest.fn();

    addHandler(spy);

    await request({url});

    expect(spy).toHaveBeenCalledWith(successResponse);
  });

  it('should not call a handler after it has been unregistered', async () => {
    const spy = jest.fn();

    addHandler(spy);
    removeHandler(spy);

    await request({url});

    expect(spy).not.toHaveBeenCalledWith(successResponse);
  });
});

describe('formatQuery', () => {
  it('should format query object into proper query string', () => {
    const query = {
      a: 1,
      b: '5=5'
    };

    expect(formatQuery(query)).toBe('a=1&b=5%3D5');
  });
});

describe('methods shortcuts functions', () => {
  const url = 'http://example.com';
  const body = 'BODY';

  beforeEach(() => {
    fetch.mockClear();
  });

  describe('put', () => {
    it('should call request with correct options', async () => {
      await put(url, body);

      const fetchPayload = fetch.mock.calls[0][1];

      expect(fetch.mock.calls[0][0]).toBe(url);
      expect(fetchPayload.body).toBe(body);
      expect(fetchPayload.method).toBe('PUT');
    });

    it('should use custom options', async () => {
      await put(url, body, {
        headers: {d: 12}
      });

      const fetchPayload = fetch.mock.calls[0][1];

      expect(fetchPayload.headers.d).toBe(12);
    });

    it('should return request response', async () => {
      expect(await put()).toBe(successResponse);
    });
  });

  describe('post', () => {
    it('should call request with correct options', async () => {
      await post(url, body);

      const fetchPayload = fetch.mock.calls[0][1];

      expect(fetch.mock.calls[0][0]).toBe(url);
      expect(fetchPayload.body).toBe(body);
      expect(fetchPayload.method).toBe('POST');
    });

    it('should use custom options', async () => {
      await post(url, body, {
        headers: {d: 12}
      });

      const fetchPayload = fetch.mock.calls[0][1];

      expect(fetchPayload.headers.d).toBe(12);
    });

    it('should return request response', async () => {
      expect(await post()).toBe(successResponse);
    });
  });

  describe('get', () => {
    const query = {param: 'q'};

    it('should call request with correct options', async () => {
      await get(url, query);

      const fetchPayload = fetch.mock.calls[0][1];

      expect(fetch.mock.calls[0][0]).toBe(url + '?param=q');
      expect(fetchPayload.method).toBe('GET');
    });

    it('should use custom options', async () => {
      await get(url, body, {
        headers: {d: 12}
      });

      const fetchPayload = fetch.mock.calls[0][1];

      expect(fetchPayload.headers.d).toBe(12);
    });

    it('should return request response', async () => {
      expect(await get()).toBe(successResponse);
    });
  });
});
