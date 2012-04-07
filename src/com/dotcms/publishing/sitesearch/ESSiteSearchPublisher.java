package com.dotcms.publishing.sitesearch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.codec.digest.DigestUtils;

import com.dotcms.publishing.DotPublishingException;
import com.dotcms.publishing.IBundler;
import com.dotcms.publishing.Publisher;
import com.dotcms.publishing.PublisherConfig;
import com.dotcms.publishing.bundlers.BundlerUtil;
import com.dotcms.publishing.bundlers.FileAssetWrapper;
import com.dotcms.publishing.bundlers.FileObjectBundler;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.portlets.fileassets.business.FileAsset;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.google.common.collect.Lists;
import com.liferay.util.FileUtil;

public class ESSiteSearchPublisher extends Publisher {

	public static final String SITE_SEARCH_INDEX = "SITE_SEARCH_INDEX";

	@Override
	public PublisherConfig init(PublisherConfig config) throws DotPublishingException {
		this.config = super.init(config);
		SiteSearchConfig myConf = (SiteSearchConfig) config;


		// if we don't specify an index, use the current one
		if (myConf.getIndexName() == null) {
			try{
				String index = APILocator.getIndiciesAPI().loadIndicies().site_search;
				if(index != null){
					myConf.setIndexName(index);
					this.config = myConf;
				}
				else{
					throw new DotPublishingException("Active Site Search Index:null");			
				}
			}
			catch(Exception e){
				throw new DotPublishingException("You must either specify a valid site search index in your PublishingConfig or have current active site search index.  Make sure you have a current active site search index by going to the site search admin portlet and creating one");
			}

		}

		return this.config;

	}

	@Override
	public PublisherConfig process() throws DotPublishingException {
		try {
			File bundleRoot = BundlerUtil.getBundleRoot(config);

			for (IBundler b : config.getBundlers()) {

				List<File> files = FileUtil.listFilesRecursively(bundleRoot, b.getFileFilter());

				List<List<File>> listsOfFiles = Lists.partition(files, 10);
				int numThreads = config.getAdditionalThreads() + 1;
				ExecutorService executor = Executors.newFixedThreadPool(numThreads);
				for (final List<File> l : listsOfFiles) {
					Runnable worker = new Runnable() {
						@Override
						public void run() {
							processFiles(l);

						}
					};
					executor.execute(worker);
				}
				executor.shutdown();
			}

			return config;
		} catch (Exception e) {
			throw new DotPublishingException(e.getMessage());

		}
	}

	private void processFiles(List<File> files) {
		for (File f : files) {
			try {
				processFileObject(f);
			} catch (IOException e) {
				Logger.info(this.getClass(), "failed: " + f + " : " + e.getMessage());

			}
		}

	}

	private void processFileObject(File file) throws IOException {
		if (file.isDirectory()){
			return;
		}
		//Logger.info(this.getClass(), "processing: " + file.getAbsolutePath());

		FileAssetWrapper wrap = (FileAssetWrapper) BundlerUtil.xmlToObject(file);
		if (wrap == null)
			return;
		// is the live guy
		if (UtilMethods.isSet(wrap.getInfo().getLiveInode()) && wrap.getInfo().getLiveInode().equals(wrap.getAsset().getInode())) {
			try {
				doPut(wrap.getAsset());
			} catch (Exception e) {
				Logger.error(this.getClass(), "site search indexPut failed: " + e.getMessage());
			}
		} else if (!UtilMethods.isSet(wrap.getInfo().getLiveInode())) {
			doDelete(wrap.getAsset());
		}

	}

	private void doDelete(FileAsset asset) {
		String url = asset.getHost() + asset.getPath() + asset.getFileName();
		Logger.info(this.getClass(), "delete: " + url);
		String md5 = DigestUtils.md5Hex(url);

		APILocator.getSiteSearchAPI().deleteFromIndex(((SiteSearchConfig) config).getIndexName(), md5);
		
		

	}

	private void doPut(FileAsset asset) throws DotPublishingException {
		try {
			SiteSearchResult res = new SiteSearchResult();
			res.setContentLength(asset.getFileSize());
			res.setHost(asset.getHost());

			Host h;

			h = APILocator.getHostAPI().find(asset.getHost(), APILocator.getUserAPI().getSystemUser(), true);
			res.setUri(asset.getPath() + asset.getFileName());
			res.setUrl(h.getHostname() + res.getUri());
			res.setFileName(asset.getFileName());

			res.setMimeType(APILocator.getFileAPI().getMimeType(asset.getFileName()));
			res.setModified(asset.getModDate());

			String md5 = DigestUtils.md5Hex(asset.getHost() + res.getUri());
			res.setId(md5);

			String x = asset.getMetaData();
			if (x != null) {

				Map<String, Object> m = com.dotmarketing.portlets.structure.model.KeyValueFieldUtil.JSONValueToHashMap(x);

				res.setAuthor((String) m.get("author"));
				res.setDescription((String) m.get("description"));
				res.setContent((String) m.get("content"));

			}

			APILocator.getSiteSearchAPI().putToIndex(((SiteSearchConfig) config).getIndexName(), res);

			Logger.info(this.getClass(), "adding: " + asset.getPath() + asset.getFileName());
		} catch (Exception e) {
			throw new DotPublishingException(e.getMessage());

		}
	}

	@Override
	public List<Class> getBundlers() {
		List<Class> list = new ArrayList<Class>();

		list.add(FileObjectBundler.class);
		// list.add(StaticHTMLPageBundler.class);
		// list.add(StaticURLMapBundler.class);
		return list;
	}

}
