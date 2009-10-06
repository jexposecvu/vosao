/**
 * Vosao CMS. Simple CMS for Google App Engine.
 * Copyright (C) 2009 Vosao development team
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * email: vosao.dev@gmail.com
 */

package org.vosao.business.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.vosao.business.ConfigBusiness;
import org.vosao.business.FolderBusiness;
import org.vosao.business.ImportExportBusiness;
import org.vosao.business.PageBusiness;
import org.vosao.business.decorators.TreeItemDecorator;
import org.vosao.entity.CommentEntity;
import org.vosao.entity.ConfigEntity;
import org.vosao.entity.FileEntity;
import org.vosao.entity.FolderEntity;
import org.vosao.entity.FormEntity;
import org.vosao.entity.PageEntity;
import org.vosao.entity.TemplateEntity;
import org.vosao.servlet.FolderUtil;
import org.vosao.servlet.MimeType;
import org.vosao.utils.DateUtil;

public class ImportExportBusinessImpl extends AbstractBusinessImpl 
	implements ImportExportBusiness {

	private static final String THEME_FOLDER = "theme/";
	
	private static final Log logger = LogFactory.getLog(ImportExportBusinessImpl.class);
	
	private FolderBusiness folderBusiness;
	private PageBusiness pageBusiness;
	private ConfigBusiness configBusiness;
	
	public void setFolderBusiness(FolderBusiness bean) {
		folderBusiness = bean;
	}
	
	public FolderBusiness getFolderBusiness() {
		return folderBusiness;
	}
	
	public void setPageBusiness(PageBusiness bean) {
		pageBusiness = bean;
	}
	
	public PageBusiness getPageBusiness() {
		return pageBusiness;
	}

	@Override
	public byte[] createExportFile(final List<TemplateEntity> list) 
			throws IOException {
		
		ByteArrayOutputStream outData = new ByteArrayOutputStream();
		ZipOutputStream out = new ZipOutputStream(outData);
		out.putNextEntry(new ZipEntry(THEME_FOLDER));
		for (TemplateEntity theme : list) {
			exportTheme(out, theme);
		}
		out.close();
		return outData.toByteArray();
	}
	
	private void exportTheme(final ZipOutputStream out, 
			final TemplateEntity theme) throws IOException {

		String themeFolder = getThemeZipPath(theme);
		addThemeResources(out, theme);		
		String descriptionName = themeFolder + "description.xml";
		out.putNextEntry(new ZipEntry(descriptionName));
		out.write(createThemeExportXML(theme).getBytes("UTF-8"));
		out.closeEntry();
		String contentName = themeFolder + "content.html";
		out.putNextEntry(new ZipEntry(contentName));
		out.write(theme.getContent().getBytes("UTF-8"));
		out.closeEntry();
	}

	private static String getThemeZipPath(final TemplateEntity theme) {
		return THEME_FOLDER + theme.getUrl() + "/";
	}
	
	private static String getThemePath(final TemplateEntity theme) {
		return "/" + THEME_FOLDER + theme.getUrl();
	}

	private String createThemeExportXML(final TemplateEntity theme) {
		Document doc = DocumentHelper.createDocument();
		Element root = doc.addElement("theme");
		root.addElement("title").addText(theme.getTitle());
		root.addElement("url").addText(theme.getUrl());
		return doc.asXML();
	}
	
	private void addThemeResources(final ZipOutputStream out, 
			final TemplateEntity theme) throws IOException {
		
		TreeItemDecorator<FolderEntity> root = getFolderBusiness().getTree();
		TreeItemDecorator<FolderEntity> themeFolder = getFolderBusiness()
				.findFolderByPath(root, getThemePath(theme));
		if (themeFolder == null) {
			return;
		}
		addResourcesFromFolder(out, themeFolder, getThemeZipPath(theme)); 
	}

	/**
	 * Add files from resource folder to zip archive.
	 * @param out - zip output stream
	 * @param folder - folder tree item
	 * @param zipPath - folder path under which resources will be placed to zip. 
	 * 	             Should not start with / symbol and should end with / symbol.
	 * @throws IOException
	 */
	private void addResourcesFromFolder(final ZipOutputStream out, 
			final TreeItemDecorator<FolderEntity> folder, final String zipPath) 
		throws IOException {
		
		out.putNextEntry(new ZipEntry(zipPath));
		out.closeEntry();
		for (TreeItemDecorator<FolderEntity> child : folder.getChildren()) {
			addResourcesFromFolder(out, child, 
					zipPath + child.getEntity().getName() + "/");
		}
		List<FileEntity> files = getDao().getFileDao().getByFolder(
				folder.getEntity().getId());
		for (FileEntity file : files) {
			String filePath = zipPath + file.getFilename();
			out.putNextEntry(new ZipEntry(filePath));
			out.write(getDao().getFileDao().getFileContent(file));
			out.closeEntry();
		}
	}

	public List<String> importZip(ZipInputStream in) throws IOException, 
			DocumentException {
		List<String> result = new ArrayList<String>();
		ZipEntry entry;
		byte[] buffer = new byte[4096];
		while((entry = in.getNextEntry()) != null) {
			if (entry.isDirectory()) {
				getFolderBusiness().createFolder("/" + entry.getName());
			}
			else {
				ByteArrayOutputStream data = new ByteArrayOutputStream();
				int len = 0;
				while ((len = in.read(buffer)) > 0) {
					data.write(buffer, 0, len);
				}
				if (isSiteContent(entry)) {
					readSiteContent(entry, data.toString("UTF-8"));
				}
				else if (isThemeDescription(entry)) {
					createThemeByDescription(entry, data.toString("UTF-8"));
				}
				else if (isThemeContent(entry)) {
					createThemeByContent(entry, data.toString("UTF-8"));
				}
				else {
					result.add(importResourceFile(entry, data.toByteArray()));
				}
			}
		}
		return result;
	}
	
	private boolean isThemeDescription(final ZipEntry entry) 
			throws UnsupportedEncodingException {
		String[] chain = FolderUtil.getPathChain(entry);
		if (chain.length < 3 || !chain[0].equals("theme")
			|| !chain[2].equals("description.xml")) {
			return false;
		}
		return true;
	}

	private TemplateEntity readThemeImportXML(final String xml) 
			throws DocumentException {
		Document doc = DocumentHelper.parseText(xml);
		Element root = doc.getRootElement();
		TemplateEntity template = new TemplateEntity();
		for (Iterator<Element> i = root.elementIterator(); i.hasNext(); ) {
            Element element = i.next();
            if (element.getName().equals("title")) {
            	template.setTitle(element.getStringValue());
            }
            if (element.getName().equals("url")) {
            	template.setUrl(element.getStringValue());
            }
        }
		return template;
	}
	
	private void createThemeByDescription(final ZipEntry entry, String xml) 
			throws UnsupportedEncodingException, DocumentException {
		String[] chain = FolderUtil.getPathChain(entry);
		String themeName = chain[1];
		TemplateEntity theme = getDao().getTemplateDao().getByUrl(themeName);
		if (theme == null) {
			theme = new TemplateEntity();
			theme.setContent("");
		}
		String content = theme.getContent();
		TemplateEntity parsedEntity = readThemeImportXML(xml);
		theme.copy(parsedEntity);
		theme.setContent(content);
		getDao().getTemplateDao().save(theme);
	}

	private boolean isThemeContent(final ZipEntry entry) 
			throws UnsupportedEncodingException {
		String[] chain = FolderUtil.getPathChain(entry);
		if (chain.length < 3 || !chain[0].equals("theme")
				|| !chain[2].equals("content.html")) {
			return false;
		}
		return true;
	}

	private void createThemeByContent(final ZipEntry entry, 
			final String content) 
			throws UnsupportedEncodingException, DocumentException {
		String[] chain = FolderUtil.getPathChain(entry);
		String themeName = chain[1];
		TemplateEntity theme = getDao().getTemplateDao().getByUrl(themeName);
		if (theme == null) {
			theme = new TemplateEntity();
			theme.setTitle(themeName);
			theme.setUrl(themeName);
		}
		theme.setContent(content);
		getDao().getTemplateDao().save(theme);
	}
	
	private String importResourceFile(final ZipEntry entry, byte[] data) 
			throws UnsupportedEncodingException {
		
		String[] chain = FolderUtil.getPathChain(entry);
		String folderPath = FolderUtil.getFilePath(entry);
		String fileName = chain[chain.length - 1];
		logger.debug("importResourceFile: " + folderPath + " " + fileName + " " + data.length);
		getFolderBusiness().createFolder(folderPath);
		TreeItemDecorator<FolderEntity> root = getFolderBusiness().getTree();
		FolderEntity folderEntity = getFolderBusiness().findFolderByPath(root, 
				folderPath).getEntity(); 
		String contentType = MimeType.getContentTypeByExt(
				FolderUtil.getFileExt(fileName));
		FileEntity fileEntity = getDao().getFileDao().getByName(
				folderEntity.getId(), fileName);
		if (fileEntity != null) {
			fileEntity.setLastModifiedTime(new Date());
			fileEntity.setSize(data.length);
		}
		else {
			fileEntity = new FileEntity(fileName, fileName, folderEntity.getId(),
					contentType, new Date(), data.length);
		}
		getDao().getFileDao().save(fileEntity);
		getDao().getFileDao().saveFileContent(fileEntity, data);
		return "/" + entry.getName();
	}

	@Override
	public byte[] createExportFile() throws IOException {
		ByteArrayOutputStream outData = new ByteArrayOutputStream();
		ZipOutputStream out = new ZipOutputStream(outData);
		out.putNextEntry(new ZipEntry(THEME_FOLDER));
		List<TemplateEntity> list = getDao().getTemplateDao().select();
		for (TemplateEntity theme : list) {
			exportTheme(out, theme);
		}
		exportContent(out);
		out.close();
		return outData.toByteArray();
	}
	
	private void exportContent(final ZipOutputStream out) throws IOException {
		String contentName = "content.xml";
		out.putNextEntry(new ZipEntry(contentName));
		out.write(createContentExportXML().getBytes("UTF-8"));
		out.closeEntry();
		addContentResources(out);
	}

	private String createContentExportXML() {
		Document doc = DocumentHelper.createDocument();
		Element root = doc.addElement("site");
		Element config = root.addElement("config");
		createConfigXML(config);
		Element pages = root.addElement("pages");
		TreeItemDecorator<PageEntity> pageRoot = getPageBusiness().getTree();
		createPageXML(pageRoot, pages);
		Element forms = root.addElement("forms");
		createFormsXML(forms);
		return doc.asXML();
	}
	
	private void createConfigXML(Element configElement) {
		ConfigEntity config = getConfigBusiness().getConfig();
		if (config.getGoogleAnalyticsId() != null) {
			Element googleAnalytics = configElement.addElement("google-analytics");
			googleAnalytics.setText(config.getGoogleAnalyticsId());
		}
		if (config.getSiteEmail() != null) {
			Element siteEmail = configElement.addElement("email");
			siteEmail.setText(config.getSiteEmail());
		}
		if (config.getSiteDomain() != null) {
			Element siteDomain = configElement.addElement("domain");
			siteDomain.setText(config.getSiteDomain());
		}
		if (config.getEditExt() != null) {
			Element editExt = configElement.addElement("edit-ext");
			editExt.setText(config.getEditExt());
		}
		if (config.getRecaptchaPrivateKey() != null) {
			Element recaptcha = configElement.addElement("recaptchaPrivateKey");
			recaptcha.setText(config.getRecaptchaPrivateKey());
		}
		if (config.getRecaptchaPublicKey() != null) {
			Element elem = configElement.addElement("recaptchaPublicKey");
			elem.setText(config.getRecaptchaPublicKey());
		}
		if (config.getCommentsEmail() != null) {
			Element elem = configElement.addElement("commentsEmail");
			elem.setText(config.getCommentsEmail());
		}
		if (config.getCommentsTemplate() != null) {
			Element elem = configElement.addElement("commentsTemplate");
			elem.setText(config.getCommentsTemplate());
		}
	}

	private void createPageXML(TreeItemDecorator<PageEntity> page,
			Element root) {
		Element pageElement = root.addElement("page"); 
		pageElement.addAttribute("url", page.getEntity().getFriendlyURL());
		pageElement.addAttribute("title", page.getEntity().getTitle());
		pageElement.addAttribute("commentsEnabled", String.valueOf(
				page.getEntity().isCommentsEnabled()));
		if (page.getEntity().getPublishDate() != null) {
			pageElement.addAttribute("publishDate", 
				DateUtil.toString(page.getEntity().getPublishDate()));
		}
		TemplateEntity template = getDao().getTemplateDao().getById(
				page.getEntity().getTemplate());
		if (template != null) {
			pageElement.addAttribute("theme", template.getUrl());
		}
		Element contentElement = pageElement.addElement("content");
		contentElement.addText(page.getEntity().getContent());
		createCommentsXML(page, pageElement);
		for (TreeItemDecorator<PageEntity> child : page.getChildren()) {
			createPageXML(child, pageElement);
		}
	}
	
	private void createCommentsXML(TreeItemDecorator<PageEntity> page, 
			Element pageElement) {
		Element commentsElement = pageElement.addElement("comments");
		List<CommentEntity> comments = getDao().getCommentDao().getByPage(
				page.getEntity().getId());
		for (CommentEntity comment : comments) {
			Element commentElement = commentsElement.addElement("comment");
			commentElement.addAttribute("name", comment.getName());
			commentElement.addAttribute("disabled", String.valueOf(
					comment.isDisabled()));
			commentElement.addAttribute("publishDate", 
				DateUtil.dateTimeToString(comment.getPublishDate()));
			commentElement.setText(comment.getContent());
		}
	}

	private void createFormsXML(Element formsElement) {
		List<FormEntity> list = getDao().getFormDao().select();
		for (FormEntity form : list) {
			createFormXML(formsElement, form);
		}
	}

	private void createFormXML(Element formsElement, final FormEntity form) {
		Element formElement = formsElement.addElement("form");
		formElement.addAttribute("name", form.getName());
		formElement.addAttribute("title", form.getTitle());
		formElement.addAttribute("email", form.getEmail());
		formElement.addAttribute("letterSubject", form.getLetterSubject());
	}
	
	private void addContentResources(final ZipOutputStream out) 
			throws IOException {
		
		TreeItemDecorator<FolderEntity> root = getFolderBusiness().getTree();
		TreeItemDecorator<FolderEntity> folder = getFolderBusiness()
				.findFolderByPath(root, "/page");
		if (folder == null) {
			return;
		}
		addResourcesFromFolder(out, folder, "page/"); 
	}

	private boolean isSiteContent(final ZipEntry entry) 
			throws UnsupportedEncodingException {
		String[] chain = FolderUtil.getPathChain(entry);
		if (chain.length != 1 || !chain[0].equals("content.xml")) {
			return false;
		}
		return true;
	}
	
	private void readSiteContent(final ZipEntry entry, final String xml) 
			throws DocumentException {
		Document doc = DocumentHelper.parseText(xml);
		Element root = doc.getRootElement();
		for (Iterator<Element> i = root.elementIterator(); i.hasNext(); ) {
            Element element = i.next();
            if (element.getName().equals("pages")) {
            	readPages(element);
            }
            if (element.getName().equals("config")) {
            	readConfigs(element);
            }
            if (element.getName().equals("forms")) {
            	readForms(element);
            }
        }
	}
	
	private void readPages(Element pages) {
		for (Iterator<Element> i = pages.elementIterator(); i.hasNext(); ) {
            Element pageElement = i.next();
            readPage(pageElement, null);
		}
	}
		
	private void readPage(Element pageElement, PageEntity parentPage) {
		String parentId = null;
		if (parentPage != null) {
			parentId = parentPage.getId();
		}
		String title = pageElement.attributeValue("title");
		String url = pageElement.attributeValue("url");
		String themeUrl = pageElement.attributeValue("theme");
		String commentsEnabled = pageElement.attributeValue("commentsEnabled");
		Date publishDate = new Date();
		if (pageElement.attributeValue("publishDate") != null) {
			try {
				publishDate = DateUtil.toDate(
						pageElement.attributeValue("publishDate"));
			} catch (ParseException e) {
				logger.error("Wrong date format " 
						+ pageElement.attributeValue("publishDate") + " " + title);
			}
		}
		TemplateEntity template = getDao().getTemplateDao().getByUrl(themeUrl);
		String templateId = null;
		if (template != null) {
			templateId = template.getId();
		}
		String content = "";
		for (Iterator<Element> i = pageElement.elementIterator(); i.hasNext(); ) {
            Element element = i.next();
            if (element.getName().equals("content")) {
            	content = element.getText();
            	break;
            }
		}
		PageEntity newPage = new PageEntity(title, content, url, parentId, 
				templateId, publishDate);
		if (commentsEnabled != null) {
			newPage.setCommentsEnabled(Boolean.valueOf(commentsEnabled));
		}
		PageEntity page = getDao().getPageDao().getByUrl(url);
		if (page != null) {
			page.copy(newPage);
		}
		else {
			page = newPage;
		}
		getDao().getPageDao().save(page);
		for (Iterator<Element> i = pageElement.elementIterator(); 
				i.hasNext(); ) {
            Element element = i.next();
            if (element.getName().equals("page")) {
            	readPage(element, page);
            }
            if (element.getName().equals("comments")) {
            	readComments(element, page);
            }
		}
	}
	
	private void readComments(Element commentsElement, PageEntity page) {
		for (Iterator<Element> i = commentsElement.elementIterator();
				i.hasNext(); ) {
            Element element = i.next();
            if (element.getName().equals("comment")) {
            	String name = element.attributeValue("name");
            	Date publishDate = new Date();
            	try {
            		publishDate = DateUtil.dateTimeToDate(
            				element.attributeValue("publishDate"));
            	}
            	catch (ParseException e) {
            		logger.error("Error parsing comment publish date " 
            				+ element.attributeValue("publishDate"));
            	}
            	boolean disabled = Boolean.valueOf(element.attributeValue(
            			"disabled"));
            	String content = element.getText();
            	CommentEntity comment = new CommentEntity(name, content, 
            			publishDate, page.getId(), disabled);
            	getDao().getCommentDao().save(comment);
            }
		}
	}
	
	private void readConfigs(Element configElement) {
		ConfigEntity config = getConfigBusiness().getConfig();
		for (Iterator<Element> i = configElement.elementIterator(); i.hasNext(); ) {
            Element element = i.next();
            if (element.getName().equals("google-analytics")) {
            	config.setGoogleAnalyticsId(element.getText());
            }
            if (element.getName().equals("email")) {
            	config.setSiteEmail(element.getText());
            }
            if (element.getName().equals("domain")) {
            	config.setSiteDomain(element.getText());
            }
            if (element.getName().equals("edit-ext")) {
            	config.setEditExt(element.getText());
            }
            if (element.getName().equals("recaptchaPrivateKey")) {
            	config.setRecaptchaPrivateKey(element.getText());
            }
            if (element.getName().equals("recaptchaPublicKey")) {
            	config.setRecaptchaPublicKey(element.getText());
            }
            if (element.getName().equals("commentsEmail")) {
            	config.setCommentsEmail(element.getText());
            }
            if (element.getName().equals("commentsTemplate")) {
            	config.setCommentsTemplate(element.getText());
            }
		}
		getDao().getConfigDao().save(config);
	}

	private void readForms(Element formsElement) {
		for (Iterator<Element> i = formsElement.elementIterator(); i.hasNext(); ) {
            Element element = i.next();
            if (element.getName().equals("form")) {
            	String name = element.attributeValue("name");
            	String title = element.attributeValue("title");
            	String email = element.attributeValue("email");
            	String letterSubject = element.attributeValue("letterSubject");
            	FormEntity form = new FormEntity(name, email, title, 
            			letterSubject);
            	getDao().getFormDao().save(form);
            }
		}		
	}

	@Override
	public ConfigBusiness getConfigBusiness() {
		return configBusiness;
	}

	@Override
	public void setConfigBusiness(ConfigBusiness bean) {
		configBusiness = bean;
	}

	@Override
	public byte[] createExportFile(FolderEntity folder) throws IOException {
		ByteArrayOutputStream outData = new ByteArrayOutputStream();
		ZipOutputStream out = new ZipOutputStream(outData);
		out.putNextEntry(new ZipEntry(THEME_FOLDER));
		TreeItemDecorator<FolderEntity> root = getFolderBusiness().getTree();
		TreeItemDecorator<FolderEntity> exportFolder = root.find(folder);
		if (exportFolder != null) {
			String zipPath = removeRootSlash(getFolderBusiness()
					.getFolderPath(folder, root)) + "/";
			addResourcesFromFolder(out, exportFolder, zipPath);
			out.close();
			return outData.toByteArray();
		}
		else {
			logger.error("folder decorator was not found " + folder.getName());
			return null;
		}
	}
	
	private String removeRootSlash(final String path) {
		if (path.charAt(0) == '/') {
			return path.substring(1);
		}
		return path;
	}
	
}