package com.xxl.job.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * httpclient util
 * @author xuxueli 2015-10-31 19:50:41
 */
public class HttpClientUtil {
	private static Logger logger = LoggerFactory.getLogger(HttpClientUtil.class);

	/**
	 * post request
	 */
	public static byte[] postRequest(String reqURL, byte[] date) throws Exception {
		byte[] responseBytes = null;
		
		HttpPost httpPost = new HttpPost(reqURL);
		//CloseableHttpClient httpClient = HttpClients.createDefault();
		CloseableHttpClient httpClient = HttpClients.custom().disableAutomaticRetries().build();	// disable retry

		try {
			// init post
			/*if (params != null && !params.isEmpty()) {
				List<NameValuePair> formParams = new ArrayList<NameValuePair>();
				for (Map.Entry<String, String> entry : params.entrySet()) {
					formParams.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
				}
				httpPost.setEntity(new UrlEncodedFormEntity(formParams, "UTF-8"));
			}*/

			// timeout
			RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectionRequestTimeout(10000)
                    .setSocketTimeout(10000)
                    .setConnectTimeout(10000)
                    .build();

			httpPost.setConfig(requestConfig);

			// data
			if (date != null) {
				httpPost.setEntity(new ByteArrayEntity(date, ContentType.DEFAULT_BINARY));
			}
			// do post
//			//test...
//			String username = "admin";
//	        String password = "123456";
//
//	        // login token
//	        String tokenTmp = DigestUtils.md5Hex(username + "_" + password);
//			tokenTmp = new BigInteger(1, tokenTmp.getBytes()).toString(16);
//			
//			Cookie cookie = new Cookie("XXL_JOB_LOGIN_IDENTITY", tokenTmp);
//			httpPost.setHeader("Cookies",cookie.toString());
			
			HttpResponse response = httpClient.execute(httpPost);
			
			HttpEntity entity = response.getEntity();
			if (null != entity) {
				responseBytes = EntityUtils.toByteArray(entity);
				EntityUtils.consume(entity);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw e;
		} finally {
			httpPost.releaseConnection();
			try {
				httpClient.close();
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}
		return responseBytes;
	}
	
	/**
	 * read bytes from http request
	 * @param request
	 * @return
	 * @throws IOException 
	 */
	public static final byte[] readBytes(HttpServletRequest request) throws IOException {
		request.setCharacterEncoding("UTF-8");
        int contentLen = request.getContentLength();
		InputStream is = request.getInputStream();
		if (contentLen > 0) {
			int readLen = 0;
			int readLengthThisTime = 0;
			byte[] message = new byte[contentLen];
			try {
				while (readLen != contentLen) {
					readLengthThisTime = is.read(message, readLen, contentLen - readLen);
					if (readLengthThisTime == -1) {
						break;
					}
					readLen += readLengthThisTime;
				}
				return message;
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
				throw e;
			}
		}
		return new byte[] {};
	}

}
