/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import React, { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { withStyles } from '@material-ui/core/styles';
import Typography from '@material-ui/core/Typography';
import Icon from '@material-ui/core/Icon';
import Button from '@material-ui/core/Button';
import ReactMarkdown from 'react-markdown';
import ReactSafeHtml from 'react-safe-html';
import API from '../../../../data/api';
import Alert from '../../../Shared/Alert';

const styles = theme => ({
    root: {
        paddingTop: theme.spacing.unit * 2,
        paddingBottom: theme.spacing.unit * 2,
    },
    docTitle: {
        fontWeight: 100,
        fontSize: 50,
        color: theme.palette.grey[500],
    },
    docBadge: {
        padding: theme.spacing.unit,
        background: theme.palette.primary.main,
        position: 'absolute',
        top: 0,
        marginTop: -22,
        color: theme.palette.getContrastText(theme.palette.primary.main),
    },
    button: {
        padding: theme.spacing.unit * 2,
        marginTop: theme.spacing.unit * 2,
    },
    displayURL: {
        padding: theme.spacing.unit * 2,
        marginTop: theme.spacing.unit * 2,
        background: theme.palette.grey[200],
        color: theme.palette.getContrastText(theme.palette.grey[200]),
        display: 'flex',
    },
    displayURLLink: {
        paddingLeft: theme.spacing.unit * 2,
    },
});
/**
 *
 *
 * @param {*} props
 * @returns
 */
function View(props) {
    const {
        classes, doc, apiId, fullScreen, intl,
    } = props;
    const [code, setCode] = useState('');
    const restAPI = new API();

    useEffect(() => {
        if (doc.sourceType === 'MARKDOWN' || doc.sourceType === 'INLINE') loadContentForDoc();
    }, [props.doc]);

    const loadContentForDoc = () => {
        const docPromise = restAPI.getInlineContentOfDocument(apiId, doc.documentId);
        docPromise
            .then((doc) => {
                setCode(doc.text);
            })
            .catch((error) => {
                if (process.env.NODE_ENV !== 'production') {
                    console.log(error);
                }
            });
    };
    /**
     * Download the document related file
     * @param {any} response Response of download file
     */
    const downloadFile = (response, doc) => {
        let fileName = '';
        const contentDisposition = response.headers['content-disposition'];

        if (contentDisposition && contentDisposition.indexOf('attachment') !== -1) {
            const fileNameReg = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/;
            const matches = fileNameReg.exec(contentDisposition);
            if (matches != null && matches[1]) fileName = matches[1].replace(/['"]/g, '');
        }
        const contentType = response.headers['content-type'];
        const blob = new Blob([response.data], {
            type: contentType,
        });
        if (typeof window.navigator.msSaveBlob !== 'undefined') {
            window.navigator.msSaveBlob(blob, fileName);
        } else {
            const URL = window.URL || window.webkitURL;
            const downloadUrl = URL.createObjectURL(blob);

            if (fileName) {
                const aTag = document.createElement('a');
                if (typeof aTag.download === 'undefined') {
                    window.location = downloadUrl;
                } else {
                    aTag.href = downloadUrl;
                    aTag.download = fileName;
                    document.body.appendChild(aTag);
                    aTag.click();
                }
            } else {
                window.location = downloadUrl;
            }

            setTimeout(() => {
                URL.revokeObjectURL(downloadUrl);
            }, 100);
        }
    };
    const handleDownload = () => {
        const promised_get_content = restAPI.getFileForDocument(apiId, doc.documentId);
        promised_get_content
            .then((done) => {
                downloadFile(done, document);
            })
            .catch((error) => {
                if (process.env.NODE_ENV !== 'production') {
                    console.log(error);
                    Alert.error(
                        intl.formatMessage({
                            id: 'documents.markdown.editor.download.error',
                            defaultMessage: 'Error downloading the file',
                        }),
                    );
                }
            });
    };
    return (
        <React.Fragment>
            {!fullScreen && <div className={classes.docBadge}>{doc.type}</div>}
            <Typography variant='h5' component='h3' className={classes.docTitle}>
                {doc.name}
            </Typography>
            <Typography variant='caption'>{doc.summary}</Typography>
            {doc.sourceType === 'MARKDOWN' && <ReactMarkdown source={code} />}
            {doc.sourceType === 'INLINE' && <ReactSafeHtml html={code} />}
            {doc.sourceType === 'URL' && (
                <a className={classes.displayURL} href={doc.sourceUrl} target='_blank'>
                    {doc.sourceUrl}
                    <Icon className={classes.displayURLLink}>open_in_new</Icon>
                </a>
            )}
            {doc.sourceType === 'FILE' && (
                <Button variant='contained' color='default' className={classes.button} onClick={handleDownload}>
                    Download
                    <Icon>arrow_downward</Icon>
                </Button>
            )}
        </React.Fragment>
    );
}

View.propTypes = {
    classes: PropTypes.shape({}).isRequired,
    doc: PropTypes.shape({}).isRequired,
    apiId: PropTypes.shape({}).isRequired,
    intl: PropTypes.shape({}).isRequired,
    fullScreen: PropTypes.shape({}).isRequired,
};

export default withStyles(styles)(View);
