if (NOT WIN32)
  return()
endif()

add_executable(ovpnagent ovpnagent.cpp)
add_core_dependencies(ovpnagent)
add_json_library(ovpnagent)
target_compile_definitions(ovpnagent PRIVATE -DOVPNAGENT_DISABLE_PATH_CHECK -DOPENVPN_AGENT_START_PROCESS)
