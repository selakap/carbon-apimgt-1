import React from 'react';
import { Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@material-ui/core';
import Button from '@material-ui/core/Button';
import PropTypes from 'prop-types';

/**
 * React component for handling confirmation dialog box.
 * TODO: Convert this to functional component ~tmkb
 * @class ConfirmDialog
 * @extends {React.Component}
 */
class ConfirmDialog extends React.Component {
    /**
     * If user confirms the action invoke the callback with true else false
     * @param {String} action One of ConfirmDialog.Action actions
     * @memberof ConfirmDialog
     */
    handleRequestClose(action) {
        const { callback } = this.props;
        if (action === ConfirmDialog.Action.OK) {
            callback(true);
        } else {
            callback(false);
        }
    }

    /**
     * @inheritDoc
     * @returns {React.Component} Confirmation box
     * @memberof ConfirmDialog
     */
    render() {
        // TODO: Use defaultProps member to assign default values to properties instead
        // of using || (logical OR) comparison
        const {
            title, message, labelCancel, labelOk, open,
        } = this.props;

        return (
            <Dialog open={open} onClose={this.handleRequestClose}>
                <DialogTitle>{title || 'Please Confirm'}</DialogTitle>
                <DialogContent>
                    <DialogContentText>{message || 'Are you sure?'}</DialogContentText>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => this.handleRequestClose(ConfirmDialog.Action.CANCEL)} color='primary'>
                        {labelCancel || 'Cancel'}
                    </Button>
                    <Button onClick={() => this.handleRequestClose(ConfirmDialog.Action.OK)} color='primary'>
                        {labelOk || 'OK'}
                    </Button>
                </DialogActions>
            </Dialog>
        );
    }
}

ConfirmDialog.propTypes = {
    title: PropTypes.string.isRequired,
    message: PropTypes.string.isRequired,
    labelCancel: PropTypes.string.isRequired,
    labelOk: PropTypes.string.isRequired,
    callback: PropTypes.func.isRequired,
    open: PropTypes.bool.isRequired,
};
ConfirmDialog.Action = {
    OK: 'ok',
    CANCEL: 'cancel',
};

export default ConfirmDialog;
