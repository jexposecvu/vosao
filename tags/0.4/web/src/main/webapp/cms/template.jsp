<%
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
%>
<%@ page contentType="text/html; charset=UTF-8"%>
<%@ include file="/WEB-INF/jsp/taglibs.jsp" %>
<html>
<head>
    <title>Template</title>
    <script type="text/javascript">
        var templateId = '<c:out value="${param.id}"/>';
    </script>
    <script src="/static/js/jquery.treeview.pack.js" type="text/javascript"></script>
    <link rel="stylesheet" href="/static/css/jquery.treeview.css" type="text/css" />
    <script src="/static/js/cms/template.js" type="text/javascript"></script>
</head>
<body>

<div id="tabs" class="ui-tabs ui-widget ui-widget-content ui-corner-all">

<ul class="ui-tabs-nav ui-helper-reset ui-helper-clearfix ui-widget-header ui-corner-all">
    <li class="ui-corner-top ui-state-active">
        <a href="#">Template</a>
    </li>
    <li class="ui-corner-top ui-state-default">
        <a id="resources" href="#">Resources</a>
    </li>
</ul>

<div id="tab-1" class="ui-tabs-panel ui-widget-content ui-corner-bottom">
<form id="templateForm">
    <div class="form-row">
        <label>Title</label>
        <input id="title" type="text" />
    </div>
    <div class="form-row">
        <label>URL name</label>
        <input id="url" type="text" />
    </div>
    <div class="form-row">
        <div>
            <input id="autosave" type="checkbox"> Autosave</input>
            Editor size: &nbsp;
            <a id="sizeLink" href="#">Big</a>&nbsp;
            <a id="wrapLink" href="#">Wrap</a>
        </div>
        <div id="contentDiv">
            <textarea id="content" rows="20" cols="80" wrap="off"></textarea>
        </div>
    </div>
    <div class="buttons">
        <input id="saveContinueButton" type="button" value="Save and continue" />
        <input id="saveButton" type="submit" value="Save" />
        <input id="cancelButton" type="button" value="Cancel" />
    </div>
</form>
</div>

</div>

</body>
</html>