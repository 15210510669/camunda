import React from 'react';
import {CopyToClipboard, Switch, Icon, LoadingIndicator} from 'components';

import './ShareEntity.scss';

export default class ShareEntity extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      loaded: false,
      isShared: false,
      id: ''
    };
  }

  componentDidMount = async () => {
    const id = await this.props.getSharedEntity(this.props.resourceId);
    this.setState({
      id,
      isShared: !!id,
      loaded: true
    });
  };

  toggleValue = async ({target: {checked}}) => {
    this.setState({
      isShared: checked
    });

    if (checked) {
      const id = await this.props.shareEntity(this.props.resourceId);
      this.setState({id});
    } else {
      await this.props.revokeEntitySharing(this.state.id);
      this.setState({id: ''});
    }
  };

  buildShareLink = () => {
    if (this.state.id) {
      return `${window.location.origin}/#/share/${this.props.type}/${this.state.id}`;
    } else {
      return '';
    }
  };

  buildShareLinkForEmbedding = () => {
    if (this.state.id) {
      return `<iframe src="${this.buildShareLink()}" frameborder="0" style="width: 1000px; height: 700px; allowtransparency; overflow: scroll"></iframe>`;
    } else {
      return '';
    }
  };

  disabled = () => {
    return !this.state.isShared;
  };

  render() {
    if (!this.state.loaded) {
      return (
        <div className="ShareEntity">
          <LoadingIndicator />
        </div>
      );
    }

    return (
      <div className="ShareEntity">
        <form>
          <div className="ShareEntity__enable">
            <div className="ShareEntity__enable-text">Enable sharing </div>
            <div className="ShareEntity__enable-switch">
              <Switch checked={this.state.isShared} onChange={this.toggleValue} />
            </div>
          </div>
          <div className={'ShareEntity__link-area' + (this.disabled() ? '--disabled' : '')}>
            <div className="ShareEntity__icon-container">
              <div className="ShareEntity__clipboard">
                <Icon type="link" renderedIn="span" />
                <span className="ShareEntity__label">Link</span>
                <span className="ShareEntity__label-description">{`Use the following URL to share the ${
                  this.props.type
                }
                with people who don't have a Camunda Optimize account:`}</span>
                <CopyToClipboard
                  className="ShareEntity__share-link"
                  disabled={this.disabled()}
                  value={this.buildShareLink()}
                />
              </div>
            </div>
            <div className="ShareEntity__icon-container">
              <div className="ShareEntity__clipboard">
                <Icon type="embed" renderedIn="span" />
                <span className="ShareEntity__label">Embed</span>
                <span className="ShareEntity__label-description">{`Use the following URL to embed the ${
                  this.props.type
                } into blogs and web pages:`}</span>
                <CopyToClipboard
                  className="ShareEntity__embed-link"
                  disabled={this.disabled()}
                  value={this.buildShareLinkForEmbedding()}
                />
              </div>
            </div>
          </div>
        </form>
      </div>
    );
  }
}
