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

import React from 'react';
import MenuItem from '@material-ui/core/MenuItem';
import FormControl from '@material-ui/core/FormControl';
import Grid from '@material-ui/core/Grid';
import Typography from '@material-ui/core/Typography';
import TextField from '@material-ui/core/TextField';
import Input from '@material-ui/core/Input';
import InputLabel from '@material-ui/core/InputLabel';
import Select from '@material-ui/core/Select';
import { withStyles } from '@material-ui/core/styles';
import PropTypes from 'prop-types';

/**
 * @inheritdoc
 * @param {*} theme theme object
 */
const styles = theme => ({
    FormControl: {
        padding: theme.spacing.unit * 2,
        width: '100%',
    },
    FormControlOdd: {
        padding: theme.spacing.unit * 2,
        backgroundColor: theme.palette.background.paper,
        width: '100%',
    },
    quotaHelp: {
        position: 'relative',
    },
});

const ApplicationCreate = (props) => {
    /**
    * This method is used to handle the updating of create application
    * request object.
    * @param {*} field field that should be updated in appliction request
    * @param {*} event event fired
    */
    const handleChange = (field, event) => {
        const { applicationRequest, updateApplicationRequest } = props;
        const newRequest = { ...applicationRequest };
        const { target: currentTarget } = event;

        switch (field) {
            case 'name':
                newRequest.name = currentTarget.value;
                break;
            case 'description':
                newRequest.description = currentTarget.value;
                break;
            case 'throttlingPolicy':
                newRequest.throttlingPolicy = currentTarget.value;
                break;
            case 'tokenType':
                newRequest.tokenType = currentTarget.value;
                break;
            default:
                break;
        }
        updateApplicationRequest(newRequest);
    };

    /**
     *
     *
     * @returns {Component}
     * @memberof ApplicationCreate
     */
    const {
        classes, throttlingPolicyList, applicationRequest, isNameValid,
    } = props;
    return (
        <form className={classes.container} noValidate autoComplete='off'>
            <Grid container spacing={24} className={classes.root}>
                <Grid item xs={12} md={6}>
                    <FormControl margin='normal' className={classes.FormControl}>
                        <TextField
                            required
                            label='Application Name'
                            InputLabelProps={{
                                shrink: true,
                            }}
                            helperText={`Enter a name to identify the Application. You will be able to pick this
                                application when subscribing to APIs`}
                            fullWidth
                            name='name'
                            onChange={e => handleChange('name', e)}
                            placeholder='My Mobile Application'
                            autoFocus
                            className={classes.inputText}
                            onBlur={e => props.validateName(e.target.value)}
                            error={!isNameValid}
                        />
                    </FormControl>

                    {throttlingPolicyList && (
                        <FormControl margin='normal' className={classes.FormControlOdd}>
                            <InputLabel htmlFor='quota-helper' className={classes.quotaHelp}>
                                    Per Token Quota
                            </InputLabel>
                            <Select
                                value={applicationRequest.throttlingPolicy}
                                onChange={e => handleChange('throttlingPolicy', e)}
                                input={<Input name='quota' id='quota-helper' />}
                            >
                                {throttlingPolicyList.map(tier => (
                                    <MenuItem key={tier} value={tier}>
                                        {tier}
                                    </MenuItem>
                                ))}
                            </Select>
                            <Typography variant='caption'>
                                    Assign API request quota per access token. Allocated quota will be shared among all
                                    the subscribed APIs of the application.
                            </Typography>
                        </FormControl>
                    )}
                    <FormControl margin='normal' className={classes.FormControl}>
                        <TextField
                            label='Application Description'
                            InputLabelProps={{
                                shrink: true,
                            }}
                            helperText='Describe the application'
                            fullWidth
                            multiline
                            rowsMax='4'
                            name='description'
                            onChange={e => handleChange('description', e)}
                            placeholder='This application is grouping apis for my mobile application'
                            className={classes.inputText}
                        />
                    </FormControl>
                </Grid>
            </Grid>
        </form>
    );
};

ApplicationCreate.propTypes = {
    classes: PropTypes.shape({}).isRequired,
};

export default withStyles(styles)(ApplicationCreate);
