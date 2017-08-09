package org.camunda.optimize.dto.engine;

import java.util.List;
import java.util.Set;

public class GroupInfoDto {

  private List<GroupDto> groups;
  private Set<UserDto> groupUsers;

  public Set<UserDto> getGroupUsers() {
    return groupUsers;
  }

  public List<GroupDto> getGroups() {
    return groups;
  }

  public void setGroupUsers(Set<UserDto> groupUsers) {
    this.groupUsers = groupUsers;
  }

  public void setGroups(List<GroupDto> groups) {
    this.groups = groups;
  }

  public boolean containsGroup(String groupId) {
    GroupDto groupDto = new GroupDto();
    groupDto.setId(groupId);
    return groups.contains(groupDto);
  }
}
