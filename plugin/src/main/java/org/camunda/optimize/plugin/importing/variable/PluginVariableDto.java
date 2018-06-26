package org.camunda.optimize.plugin.importing.variable;


public class PluginVariableDto {

  /*
   * The id of the variable.
   */
  private String id;

  /*
   * The name of the variable.
   *
   * Note: This field is required in order to be imported to Optimize.
   */
  private String name;

  /*
   * The type of the variable. This can be all primitive types that are supported by the engine.
   * In particular, String, Integer, Long, Short, Double, Boolean, Date.
   *
   * Note: This field is required in order to be imported to Optimize.
   */
  private String type;

  /*
   * The value of the variable.
   */
  private String value;

  /*
   * The process definition key of the process model, where the variable was created.
   *
   * Note: This field is required in order to be imported to Optimize.
   */
  private String processDefinitionKey;

  /*
   * The process definition id of the process model, where the variable was used.
   *
   * Note: This field is required in order to be imported to Optimize.
   */
  private String processDefinitionId;

  /*
   * The process instance id of the process instance, where the variable was used.
   *
   * Note: This field is required in order to be imported to Optimize.
   */
  private String processInstanceId;

  /**
   * The version of the variable value. While a process instance is running
   * the same variable can be updated several times. This value indicates
   * which update number this variable is.
   *
   * Note: This field is required in order to be imported to Optimize.
   */
  private Long version;

  /**
   *  The field states the engine the variable is coming from.
   *  In Optimize you can configure multiple engines to import data from.
   *  Each engine configuration should have an unique engine alias associated
   *  with it.
   *
   * Note: This field is required in order to be imported to Optimize.
   */
  private String engineAlias;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  public String getEngineAlias() {
    return engineAlias;
  }

  public void setEngineAlias(String engineAlias) {
    this.engineAlias = engineAlias;
  }
}
