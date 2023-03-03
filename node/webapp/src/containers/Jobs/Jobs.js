import React, {Component, Fragment} from 'react';
import {Button, Input, Modal, PageHeader, Row, Select, Table, Tag, Icon, Tabs, Tooltip} from 'antd';
import xhr from "axios/index";
import {isJSON} from "../../utils/isJson";
import moment from "moment";
import {Sorter as sorterUtil} from "../../utils/sorterUtil";

const {TextArea} = Input;
const {Option} = Select;
const {TabPane} = Tabs;
const {confirm} = Modal;

const API_URL = process.env.API_URL;
const API_URLS = JSON.parse(process.env.API_URLS);
const DS_SIZE = process.env.DATASOURCE_SIZE_PER_RETRIEVAL;
const LOCALE = process.env.LOCALE;
const TIMEZONE = process.env.TIMEZONE;
const RANDOMNESS_LOG = 'randomnesslog';
const PRICE_FEED = 'runlog';

class Jobs extends Component {

  constructor() {
    super();
    this.state = {
      loading: true,
      warning: false,
      visible: false,
      currentBackEndPage: 1,
      vrfCurrentBackEndPage: 1,
      totalDataSource: [],
      totalVRF: [],
      vrfDataSource: [],
      dataSource: [],
      jobUrl: API_URL,
      providerList: [],
    };

    const paginationOptions = {
      total: 0,
      current: 1,
      pageSize: 10,
      pageSizeOptions: ['3', '10', '25', '50', '100'],
      showTitle: true,
      showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} items`,
      showSizeChanger: false,
      showQuickJumper: true,
      size: "small",
    };
    this.state.paginationOptions = {...paginationOptions};
    this.state.vrfPaginationOptions = {...paginationOptions};

  };

  componentDidMount() {
    this.setState({loading: true});
    new Promise(resolve => this.getJobs(this.state.currentBackEndPage, DS_SIZE, PRICE_FEED, resolve))
      .then(() => this.handleTableChange(this.state.paginationOptions, null, null, PRICE_FEED))
      .then(() => this.setState({loading:false}));

    if (this.props.location.state && this.props.location.state.create && this.props.location.state.jobUrl) {
      this.setState({jobUrl: this.props.location.state.jobUrl});
      this.showModal();
      let input = eval('(' + this.props.location.state.code + ')');
      this.setState({textValue: JSON.stringify(input, null, 4)});
    }
    window.sessionStorage.removeItem('jobUrl');
    window.sessionStorage.removeItem('nodeName');
  };

  componentWillReceiveProps() {
  };

  getColumnSearchProps = dataIndex => ({
    filterDropdown: ({ setSelectedKeys, selectedKeys, confirm, clearFilters }) => (
        <div style={{ padding: 8 }}>
          <Input
              ref={ node => {
                this.searchInput = node;
              }}
              placeholder={`Search ${dataIndex}`}
              value={selectedKeys[0]}
              onChange={e => setSelectedKeys(e.target.value ? [e.target.value]: [])}
              onPressEnter={() => this.handleSearch(selectedKeys, confirm, dataIndex)}
              style={{ width: 188, marginBottom: 8, display: 'block' }}
          />
          <Button
              type = 'primary'
              onClick={() => this.handleSearch(selectedKeys, confirm, dataIndex)}
              icon="search"
              size="small"
              style={{ width: 90, marginRight: 8 }}
          >
            Search
          </Button>
          <Button
              onClick={() => this.handleReset(clearFilters)}
              size="small"
              style={{ width: 90 }}
          >
            Reset
          </Button>
        </div>
    ),
    filterIcon: filtered => (
        <Icon type="search" style={{ color: filtered ? '#1890ff' : undefined }}/>
    ),
    onFilter: (value, record) => record[dataIndex].toString().includes(value),
    onFilterDropdownVisibleChange: visible => {
      if(visible) {
        setTimeout(() => this.searchInput.select());
      }
    },
    render(text) {
      return text;
    },
  });

  handleSearch = (selectedKeys, confirm, dataIndex) => {
    confirm();
  }

  handleReset = clearFilters => {
    clearFilters();
  };

  getJobs = (page, size, type, callback) => {
    let dsSizeArr = [];
    let promises = [];
    API_URLS.map(api => {
      const url = (type) => `${api.value}/job/specs/active?type=${type}&page=${page}&size=${size}`;
      promises.push(xhr.get(url(type), {timeout: 1000 * 10})
          .then(response => response.data)
          .then(response => {
            response.data.map(job => this.filterJobsAndSetToState(this.createJob(job, api)));
            dsSizeArr.push(response.count);
          })
          .catch(e => console.log(`${api.text} is down`)));
    });
    Promise.all(promises)
        .then(() => {
          if (type === PRICE_FEED) {
            this.setState({totalDataSource: dsSizeArr, currentBackEndPage: Math.max(this.state.currentBackEndPage, ++page)});
            this.state.paginationOptions.total = dsSizeArr.reduce((a, b) => a + b, 0);
          } else {
            this.setState({totalVRF: dsSizeArr, vrfCurrentBackEndPage: Math.max(this.state.vrfCurrentBackEndPage, ++page)});
            this.state.vrfPaginationOptions.total = dsSizeArr.reduce((a, b) => a + b, 0);
          }
        })
        .then(() => { if (callback) callback(); });
  }

  createJob = (data, api) => {
    const params = JSON.parse(data.params);
    let result, lastRun;
    if (data.result) {
       let r = data.result.split(",");
       result = r[0] || "-";
      lastRun = r[1] || 0;
    }

    return {
      key: data.address + data.jobSpecsId + api.text,
      Contract: data.address,
      ID: data.jobSpecsId,
      Initiator: data.type,
      LastRun: {
        date: (lastRun !== 0) ? new Date(lastRun).toLocaleString(LOCALE, {timeZone: TIMEZONE}) : "-",
        epoch: (lastRun !== 0) ? moment(lastRun).unix() : 0,
      },
      Updated: {
        date: new Date(data.updatedAt).toLocaleString(LOCALE, {timeZone: TIMEZONE}),
        epoch: moment(data.updatedAt).unix(),
      },
      Node: api.text,
      DataSource: {
        task: JSON.parse(data.params).tasks.map(task => task.type).join(" / "),
        ...(this.generateTagColor(params.tasks[0]))
      },
      LastRunResult: { value: result, url: `${api.value}/job/result/${data.jobSpecsId}` },
      Code: JSON.stringify(params, null, 2),
      PublicKey: (params.initiators[0].type === RANDOMNESS_LOG) ?
          params.tasks[0].params.publicKey : null,
    };
  }

  filterJobsAndSetToState = (job) => {
    if (job.Initiator === RANDOMNESS_LOG) {
      if (this.state.vrfDataSource.findIndex(o => o.key === job.key) < 0) {
        this.setState({
          vrfDataSource: [...this.state.vrfDataSource, job],
        });
      }
    } else {
      if (this.state.dataSource.findIndex(o =>  o.key === job.key) < 0) {
        this.setState({
          dataSource: [...this.state.dataSource, job],
        });
      }
    }
  }

  onSelectChange = (e) => {
    this.setState({jobUrl: e});
  };

  showModal = () => {
    if (!this.state.jobUrl) { this.setState({jobUrl: API_URL}); }
    this.setState({
      visible: true,
      textValue: ''
    });
  };

  handleOk = async (e) => {

    if (!isJSON(this.state.textValue)) {
      this.setState({warning: true});
      return;
    }
    this.setState({warning: false, visible: false});
    await xhr.post(`${this.state.jobUrl}/job/specs`, JSON.parse(this.state.textValue)).then((result) => {

     if (result.error) {
       this.error(result.error)
     }
     if (result.data.msg === 'success') {
       this.success()
     } else {
       this.error(result.data.msg)
     }
   }).catch((e) => {
     this.error(e.toString())
   });
  };

  handleCancel = e => {
    this.setState({
      visible: false,
      jobUrl: '',
      edit: '',
      create: '',
      warning: false,
    });
    delete this.props.location.state;
  };

  onTextChange = (e) => {
    this.setState({textValue: e.target.value})
  };

  success = () => {
    Modal.success({
      content: 'Successful!',
      onOk: () => window.location.reload(true),
    });
  };

  error = (message) => {
    Modal.error({
      content: message,
    });
  };

  generateTagColor = (task) => {
    let url, tag, color ;

    url = (task.type === "httpget" || task.type === "converttrx") ? task.params.get : task.type;

    try {
      const host = new URL(url).host;
      tag = host.split(".").at(-2).toUpperCase();
      color = "blue";
    } catch (e) {
      if (e instanceof TypeError) {
        tag = url.toUpperCase();
        switch (tag) {
          case "JUSTSWAP":
            color = "cyan";
            break;
          case "RANDOM":
            tag = "VRF";
            color = "green";
            break;
          default:
            tag = "UNKNOWN";
            color = "orange";
        }
      } else {
        tag = "UNKNOWN";
        color = 'red';
      }
    }

    let exists = this.state.providerList.findIndex(o => o.text === tag);

    if (exists === -1) {
      this.setState({
        providerList: [...this.state.providerList, {text: tag, value: tag}],
      });
    }

    return { tag: tag,color: color};
  }

  showDeleteConfirm = (record) => {
    confirm({
      title: 'Confirm job spec deletion?',
      content: 'Job spec will be archived',
      okText: 'Confirm',
      okType: 'danger',
      cancelText: 'Cancel',
      onOk() {
        const selectedNode = API_URLS.find(url => url.text === record.Node).value;
        const url = `${selectedNode}/job/specs/${record.ID}`;
        xhr.delete(url).then((result) => {
          if (result.error) {
            Modal.error({content: result.error})
          }
          if(result.data && result.data.msg && result.data.msg === 'success'){
            Modal.success({content: result.data.msg, onOk: () => window.location.reload(true)});
          } else{
            Modal.error({content: result.data.msg});
          }
        }).catch((e) => {
          Modal.error({content: e})
        });
      },
      onCancel() {
      },
    });
  }

  handleTableChange = (pagination, filters, sorter, type = PRICE_FEED) => {
    let minPage, pager, dataSource, dataSourceCount, maxPage;
    if (type === PRICE_FEED) {
      pager = { ...this.state.paginationOptions, current: pagination.current };
      dataSource = this.state.dataSource;
      dataSourceCount = this.state.totalDataSource;
      minPage = this.state.currentBackEndPage;
    } else {
      pager = { ...this.state.vrfPaginationOptions, current: pagination.current };
      dataSource = this.state.vrfDataSource;
      dataSourceCount = this.state.totalVRF;
      minPage = this.state.vrfCurrentBackEndPage;
    }
    const serverCount = dataSourceCount.length;
    const offset = pager.current * pager.pageSize;

    if ((dataSource.length === 0 || dataSource.length < dataSourceCount.reduce((a, b) => a + b, 0)) && (offset > dataSource.length)) {

      const maxBEPage = (Math.max(Math.ceil(Math.max(...dataSourceCount)/pager.pageSize ), 1));
      const numLoopsNeeded = Math.ceil((offset - dataSource.length)/ (serverCount * pager.pageSize));
      maxPage = Math.min(minPage+numLoopsNeeded+1, maxBEPage);

      const promises = [];
      this.setState({loading: true});
      for (let i = minPage; i <= maxPage; i++) {
        promises.push(new Promise(resolve => this.getJobs(i, pager.pageSize, type, resolve)));
      }
      Promise.all(promises).then(() => this.setState({loading: false}));
    }

    (type === PRICE_FEED) ?
        this.setState({paginationOptions: pager}) :
        this.setState({vrfPaginationOptions: pager}) ;
  }

  handleOnTabClick = (key) => {
   if (key === "2") {
     this.handleTableChange(this.state.vrfPaginationOptions, null, null, RANDOMNESS_LOG)
   } else {
     this.handleTableChange(this.state.paginationOptions, null, null, PRICE_FEED)
   }
  }

  render() {
    let {
      visible,
      loading,
      textValue,
      warning,
      paginationOptions,
      vrfPaginationOptions,
      dataSource,
      vrfDataSource,
      providerList,
    } = this.state;

    const commonColumns = [
      {
        title: 'Contract Address',
        dataIndex: 'Contract',
        key: 'Contract',
        ellipsis: true,
        ...this.getColumnSearchProps('Contract'),
        sorter: (a,b, sortOrder) => sorterUtil.STRING(a.Contract, b.Contract, sortOrder),
      },
      {
        title: 'Node',
        dataIndex: 'Node',
        key: 'Node',
        ellipsis: true,
        filters: API_URLS,
        onFilter: (record, {Node}) => record.toLowerCase().includes(API_URLS.find((url)=> url.text === Node).value),
        sorter: (a,b, sortOrder) => sorterUtil.STRING(a.Node, b.Node, sortOrder),
      },
      {
        title: 'Job ID',
        dataIndex: 'ID',
        ellipsis: true,
        ...this.getColumnSearchProps('ID'),
        key: 'ID',
        sorter: (a, b) => sorterUtil.DEFAULT(a.ID, b.ID),
      },
      {
        title: 'Last Updated time',
        dataIndex: 'Updated.date',
        key: 'Updated',
        ellipsis: true,
        sorter: (a, b, sortOrder) => sorterUtil.DATE(a.Updated.epoch, b.Updated.epoch, sortOrder),
        sortDirection: ['descend', 'ascend'],
      },
      {
        title: 'Last Run Time',
        dataIndex: 'LastRun.date',
        key: 'LastRun',
        ellipsis: true,
        sorter: (a, b, sortOrder) => sorterUtil.DATE(a.LastRun.epoch, b.LastRun.epoch, sortOrder),
        defaultSortOrder: 'descend',
        sortDirection: ['descend', 'ascend'],
      },
      {
        title: 'Last Run Result',
        dataIndex: 'LastRunResult.value',
        key: 'LastRunResultValue',
        ellipsis: true,
      },
      {
        title: 'Current Result',
        dataIndex: 'LastRunResult.url',
        key: 'LastRunResultURL',
        width: 90,
        render: lastRunResult => {
          if (lastRunResult) {
            return <a href={lastRunResult} target="_blank" rel="noopener noreferrer">Result</a>;
          }
        }
      },
    ]

    const commonActions = [
      {
        title: 'Actions',
        width: 110,
        render: (record) => {
          return <div>
            <Button.Group size={'small'}>
              <Tooltip title={'view'}>
                <Button icon='select'
                        className={'wink-btn-success'}
                        onClick={event => {
                          let selectedNode = API_URLS.find(url => url.text === record.Node).value;
                          window.sessionStorage.setItem('jobUrl', selectedNode);
                          window.sessionStorage.setItem('nodeName', record.Node);
                          this.props.history.push({pathname: "/jobs/" + record.ID, state: {jobUrl: selectedNode, nodeName: record.Node}});
                        }} />
              </Tooltip>
              <Tooltip title={'edit'}>
                <Button icon='edit'
                        className={'wink-btn-primary'}
                        onClick={event => {
                          const selectedNode = API_URLS.find(url => url.text === record.Node).value;
                          this.setState({code:JSON.stringify(JSON.parse(record.Code)), create:true, edit:true, jobUrl: selectedNode }, () => {
                            this.showModal();
                            let input = eval('(' + this.state.code + ')');
                            this.setState({textValue: JSON.stringify(input, null, 4),  jobUrl: selectedNode});
                          });
                        }}
                />
              </Tooltip>
            </Button.Group>
            &nbsp;
            <Button.Group size={'small'}>
              <Tooltip title={'delete'}>
                <Button type='danger'
                        icon='delete'
                        onClick={ event => this.showDeleteConfirm(record)}
                />
              </Tooltip>
            </Button.Group>
          </div>
        }
      }
    ];

    const columns = [
        ...commonColumns,
      {
        title: 'Data Source',
        dataIndex: 'DataSource',
        key: 'DataSource',
        width: 130,
        filters: providerList,
        onFilter: (value, record) => value.toUpperCase().includes(record.DataSource.tag.toUpperCase()),
        render: dataSource => (
            <span>
                <Tooltip title={dataSource.task} overlayStyle={{'whiteSpace': 'nowrap', maxWidth: '500px'}} >
                  <Tag color={dataSource.color}>
                    {dataSource.tag}
                  </Tag>
                </Tooltip>
             </span>
        ),
      },
        ...commonActions
    ];

    const vrfColumns = [
        ...commonColumns,
      {
        title: 'Public Key',
        dataIndex: 'PublicKey',
        key: 'PublicKey',
        width: 200,
        ...this.getColumnSearchProps('PublicKey'),
      },
        ...commonActions
    ];

    return <Fragment>
      <PageHeader title="Jobs">

        <Button size='large' onClick={this.showModal} style={{float: 'right'}}>
          Create Job
        </Button>

      </PageHeader>

      <Tabs
          defaultActiveKey="1"
          onTabClick={this.handleOnTabClick}
      >
      <TabPane tab={<span> <Icon type="stock" />Price Feed Jobs</span>}
               key="1">
      <Row gutter={16}>

        <Table
            dataSource={dataSource}
            columns={columns}
            loading={loading}
            pagination={paginationOptions}
            onChange={(pagination, filters, sorter) => this.handleTableChange(pagination, filters, sorter, PRICE_FEED)}
            // scroll={{ y: "calc(100vh - 400px)" }}
        />


      </Row>
      </TabPane>
        <TabPane tab={<span><Icon type="question" />VRF</span>} key="2">

        <Table
            dataSource={vrfDataSource}
            columns={vrfColumns}
            loading={loading}
            pagination={vrfPaginationOptions}
            onChange={(pagination, filters, sorter) => this.handleTableChange(pagination, filters, sorter, RANDOMNESS_LOG)}
            // scroll={{ y: "calc(100vh - 400px)" }}
        />

      </TabPane>
      </Tabs>

      <Modal
          visible={visible}
          title="Create Job"
          onOk={this.handleOk}
          onCancel={this.handleCancel}
          footer={[
            <Button key="back" onClick={this.handleCancel}>
              Cancel
            </Button>,
            <Button key="submit" type="primary" onClick={this.handleOk}>
              Create
            </Button>
          ]}
      >
        <div>
          <span>Host Node</span>
          <Select
              defaultActiveFirstOption={true}
              defaultValue={this.state.jobUrl || API_URL}
              value={this.state.jobUrl || API_URL}
              autoFocus={true}
              onSelect={this.onSelectChange}
              optionLabelProp='label'
              style={{ width: '100%', marginBottom: 16 }}
              disabled={!!this.props.location.state || !!this.state.edit}
          >
            {API_URLS.map(url => (
                <Option key={url.value} value={url.value} label={url.text + " - " + url.value}>{url.text + " - " + url.value}</Option>
            ))}
          </Select>
        </div>
        <TextArea
            label="JSON Blob"
            rows={10}
            placeholder="Paste JSON"
            value={textValue}
            onChange={this.onTextChange}
        />
        {warning && <span key="warning" style={{float: 'left', color: 'red'}}>Not a valid JSON</span>}
        {!warning && <span key="warning"
                           style={{float: 'left', color: 'red'}}></span>}
      </Modal>


    </Fragment>
  }
}

export default Jobs;
