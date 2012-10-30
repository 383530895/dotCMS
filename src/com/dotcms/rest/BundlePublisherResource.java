package com.dotcms.rest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.FileUtils;

import com.dotcms.publisher.business.DotPublisherException;
import com.dotcms.publisher.business.EndpointDetail;
import com.dotcms.publisher.business.PublishAuditAPI;
import com.dotcms.publisher.business.PublishAuditHistory;
import com.dotcms.publisher.business.PublishAuditStatus;
import com.dotcms.publisher.business.PublisherQueueJob;
import com.dotcms.publisher.endpoint.bean.PublishingEndPoint;
import com.dotcms.publisher.endpoint.business.PublisherEndpointAPI;
import com.dotcms.publisher.receiver.BundlePublisher;
import com.dotcms.publishing.PublisherConfig;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.cms.factories.PublicEncryptionFactory;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.util.ConfigUtils;
import com.dotmarketing.util.Logger;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

@Path("/bundlePublisher")
public class BundlePublisherResource extends WebResource {
	public static String MY_TEMP = "";
	private PublisherEndpointAPI endpointAPI = APILocator.getPublisherEndpointAPI();
	private PublishAuditAPI auditAPI = PublishAuditAPI.getInstance();

	@POST
	@Path("/publish")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response publish(
			@FormDataParam("bundle") InputStream bundle,
			@FormDataParam("bundle") FormDataContentDisposition fileDetail,
			@FormDataParam("AUTH_TOKEN") String auth_token_enc,
			@Context HttpServletRequest req) {
		
		try {
			String auth_token = PublicEncryptionFactory.decryptString(auth_token_enc);
			String remoteIP = req.getRemoteAddr();
			PublishingEndPoint mySelf = endpointAPI.findSenderEndpointByAddress(remoteIP);
			
			if(!isValidToken(auth_token, remoteIP, mySelf)) {
				bundle.close();
				return Response.status(HttpStatus.SC_UNAUTHORIZED).build();
			}
			
			String bundleName = fileDetail.getFileName();
			String bundlePath = ConfigUtils.getBundlePath()+File.separator+MY_TEMP;
			String bundleFolder = bundleName.substring(0, bundleName.indexOf(".tar.gz"));
			
			updateAuditTable(mySelf, bundleFolder);
			
			//Write file on FS
			writeToFile(bundle, bundlePath+bundleName);
			
			
			//Configure and Invoke the Publisher
			Logger.info(PublisherQueueJob.class, "Started bundle publish process");
			
			PublisherConfig pconf = new PublisherConfig();
			BundlePublisher bundlePublisher = new BundlePublisher();
			pconf.setId(bundleName);
			pconf.setEndpoint(mySelf.getId());
			bundlePublisher.init(pconf);
			bundlePublisher.process(null);
			
			Logger.info(PublisherQueueJob.class, "Finished bundle publish process");
			
			return Response.status(HttpStatus.SC_OK).build();
		} catch (NumberFormatException e) {
			Logger.error(PublisherQueueJob.class,e.getMessage(),e);
		} catch (Exception e) {
			Logger.error(PublisherQueueJob.class,e.getMessage(),e);
		}
		
		return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
	}

	private void updateAuditTable(PublishingEndPoint mySelf, String bundleFolder)
			throws DotPublisherException {
		//Status
		PublishAuditStatus status =  new PublishAuditStatus(bundleFolder);
		//History
		PublishAuditHistory historyPojo = new PublishAuditHistory();
		EndpointDetail detail = new EndpointDetail();
		detail.setStatus(PublishAuditStatus.Status.RECEIVED_BUNDLE.getCode());
		detail.setInfo("Received bundle");
		
		historyPojo.addOrUpdateEndpoint(mySelf.getId(), detail);
		status.setStatus(PublishAuditStatus.Status.RECEIVED_BUNDLE);
		
		//Insert in Audit table
		auditAPI.insertPublishAuditStatus(status);
	}
	
	private boolean isValidToken(String token, String remoteIP, PublishingEndPoint mySelf) throws IOException, DotDataException {
		String clientKey = token;
		
		//My key
		String myKey = null;
		if(mySelf != null) {
			myKey = retriveKeyString(
					PublicEncryptionFactory.decryptString(mySelf.getAuthKey().toString()));
		} else {
			return false;
		}
		
		
		return clientKey.equals(myKey);
			
	}
	
	private String retriveKeyString(String token) throws IOException {
		String key = null;
		if(token.contains(File.separator)) {
			File tokenFile = new File(token);
			if(tokenFile != null && tokenFile.exists())
				key = FileUtils.readFileToString(tokenFile, "UTF-8").trim();
		} else {
			key = token;
		}
		
		return key;
	}

	
	// save uploaded file to new location
	private void writeToFile(InputStream uploadedInputStream,
			String uploadedFileLocation) {

		try {
			OutputStream out = new FileOutputStream(new File(
					uploadedFileLocation));
			int read = 0;
			byte[] bytes = new byte[1024];

			out = new FileOutputStream(new File(uploadedFileLocation));
			while ((read = uploadedInputStream.read(bytes)) != -1) {
				out.write(bytes, 0, read);
			}
			out.flush();
			out.close();
		} catch (IOException e) {

			e.printStackTrace();
		}

	}
}
