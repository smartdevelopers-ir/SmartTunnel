//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License Version 3
//    as published by the Free Software Foundation.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU Affero General Public License for more details.
//
//    You should have received a copy of the GNU Affero General Public License
//    along with this program in the COPYING file.
//    If not, see <http://www.gnu.org/licenses/>.

#ifndef OPENVPN_CLIENT_ASYNC_RESOLVE_H
#define OPENVPN_CLIENT_ASYNC_RESOLVE_H

#ifdef USE_ASIO
#include <openvpn/client/async_resolve/asio.hpp>
#else
#include <openvpn/client/async_resolve/generic.hpp>
#endif

// create shortcuts for common templated classes
namespace openvpn {
typedef AsyncResolvable<openvpn_io::ip::udp::resolver> AsyncResolvableUDP;
typedef AsyncResolvable<openvpn_io::ip::tcp::resolver> AsyncResolvableTCP;
} // namespace openvpn

#endif /* OPENVPN_CLIENT_ASYNC_RESOLVE_H */
