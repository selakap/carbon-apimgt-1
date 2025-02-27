import React from 'react';
import { IconButton, Toolbar, AppBar } from '@material-ui/core';
import { withStyles } from '@material-ui/core/styles';
import MenuIcon from '@material-ui/icons/Menu';
import SearchIcon from '@material-ui/icons/SearchOutlined';
import Hidden from '@material-ui/core/Hidden';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import VerticalDivider from 'AppComponents/Shared/VerticalDivider';

import Avatar from './avatar/Avatar';
import HeaderSearch from './headersearch/HeaderSearch';
import GlobalNavBar from './navbar/GlobalNavBar';

const styles = theme => ({
    appBar: {
        // zIndex: theme.zIndex.modal + 1,
        position: 'relative',
        background: theme.palette.background.appBar,
    },
    typoRoot: {
        marginLeft: theme.spacing.unit * 3,
        marginRight: theme.spacing.unit * 3,
        textTransform: 'capitalize',
    },
    brandLink: {
        color: theme.palette.primary.contrastText,
    },
    toolbar: {
        minHeight: 56,
        [`${theme.breakpoints.up('xs')} and (orientation: landscape)`]: {
            minHeight: 48,
        },
        [theme.breakpoints.up('sm')]: {
            minHeight: 64,
        },
    },
    menuIcon: {
        color: theme.palette.getContrastText(theme.palette.background.appBar),
        fontSize: 35,
    },
});

/**
 * Construct the Global AppBar header section
 * @class Header
 * @extends {React.Component}
 */
class Header extends React.Component {
    /**
     *Creates an instance of Header.
     * @param {Object} props @inheritdoc
     * @memberof Header
     */
    constructor(props) {
        super(props);
        this.state = {
            openNavBar: false,
            smScreen: false,
        };
        this.toggleGlobalNavBar = this.toggleGlobalNavBar.bind(this);
        this.toggleSmSearch = this.toggleSmSearch.bind(this);
    }

    /**
     * Toggle the Global LHS Navbar visibility
     *
     * @memberof Header
     */
    toggleGlobalNavBar() {
        this.setState({ openNavBar: !this.state.openNavBar });
    }

    /**
     * Show search input in sm breakpoint or lower resolution
     */
    toggleSmSearch() {
        this.setState({ smScreen: !this.state.smScreen });
    }

    /**
     *
     * @inheritdoc
     * @returns {React.ComponentClass} @inheritdoc
     * @memberof Header
     */
    render() {
        const { openNavBar, smScreen } = this.state;
        const { classes, avatar, theme } = this.props;
        return (
            <React.Fragment>
                <AppBar className={classes.appBar} position='fixed'>
                    <Toolbar className={classes.toolbar}>
                        <IconButton onClick={this.toggleGlobalNavBar}>
                            <MenuIcon className={classes.menuIcon} />
                        </IconButton>
                        <Link to='/'>
                            <img src={theme.custom.logo} alt={theme.custom.title} />
                        </Link>
                        <VerticalDivider height={32} />
                        <Hidden smDown>
                            <HeaderSearch />
                        </Hidden>
                        <Hidden mdUp>
                            <IconButton onClick={this.toggleSmSearch} color='inherit'>
                                <SearchIcon className={classes.menuIcon} />
                            </IconButton>
                            {smScreen && <HeaderSearch toggleSmSearch={this.toggleSmSearch} smSearch={smScreen} />}
                        </Hidden>
                        {avatar}
                    </Toolbar>
                </AppBar>
                <GlobalNavBar toggleGlobalNavBar={this.toggleGlobalNavBar} open={openNavBar} />
            </React.Fragment>
        );
    }
}
Header.defaultProps = {
    avatar: <Avatar />,
};

Header.propTypes = {
    classes: PropTypes.shape({
        appBar: PropTypes.string,
        menuIcon: PropTypes.string,
        toolbar: PropTypes.string,
    }).isRequired,
    avatar: PropTypes.element,
    theme: PropTypes.shape({
        custom: PropTypes.shape({
            logo: PropTypes.string,
            title: PropTypes.string,
        }),
    }).isRequired,
};

export default withStyles(styles, { withTheme: true })(Header);
