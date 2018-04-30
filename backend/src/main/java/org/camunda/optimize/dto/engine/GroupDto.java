package org.camunda.optimize.dto.engine;

public class GroupDto {

  protected String id;
  protected String name;
  protected String type;

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setId(String id) {
    this.id = id;
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

  @Override
  public boolean equals(Object object) {
    if( object instanceof GroupDto) {
      GroupDto anotherGroupDto = (GroupDto) object;
      return this.id.equals(anotherGroupDto.id);
    }
    return false;
  }
}
