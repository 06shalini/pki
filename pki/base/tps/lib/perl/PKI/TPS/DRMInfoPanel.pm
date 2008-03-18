#!/usr/bin/pkiperl
#
# --- BEGIN COPYRIGHT BLOCK ---
# This library is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation;
# version 2.1 of the License.
# 
# This library is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
# 
# You should have received a copy of the GNU Lesser General Public
# License along with this library; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor,
# Boston, MA  02110-1301  USA 
# 
# Copyright (C) 2007 Red Hat, Inc.
# All rights reserved.
# --- END COPYRIGHT BLOCK ---
#

use strict;
use warnings;
use PKI::TPS::GlobalVar;
use PKI::TPS::Common;
use URI::URL;

package PKI::TPS::DRMInfoPanel;
$PKI::TPS::DRMInfoPanel::VERSION = '1.00';

use PKI::TPS::BasePanel;
our @ISA = qw(PKI::TPS::BasePanel);

sub new { 
    my $class = shift;
    my $self = {}; 

    $self->{"isSubPanel"} = \&is_sub_panel;
    $self->{"hasSubPanel"} = \&has_sub_panel;
    $self->{"isPanelDone"} = \&PKI::TPS::Common::no;
    $self->{"getPanelNo"} = &PKI::TPS::Common::r(6);
    $self->{"getName"} = &PKI::TPS::Common::r("DRM Information");
    $self->{"vmfile"} = "drminfopanel.vm";
    $self->{"update"} = \&update;
    $self->{"panelvars"} = \&display;
    bless $self,$class; 
    return $self; 
}

sub is_sub_panel
{
    my ($q) = @_;
    return 0;
}

sub has_sub_panel
{
    my ($q) = @_;
    return 0;
}

sub validate
{
    my ($q) = @_;
    &PKI::TPS::Wizard::debug_log("DRMInfoPanel: validate");
    return 1;
}

sub update
{
    my ($q) = @_;
    &PKI::TPS::Wizard::debug_log("DRMInfoPanel: update");

    my $choice = $q->param('choice');
    $::config->put("preop.krainfo.keygen", $choice);

    if ($choice eq "keygen") {
      my $count = $q->param('urls');
      my $instanceID = $::config->get("service.instanceID");
      my $host = "";
      my $port = "";
      if ($count =~ /http/) {
        my $info = new URI::URL($count);
        $host = $info->host;
        $port = $info->port;
      } else {
        $host = $::config->get("preop.securitydomain.kra$count.host");
        $port = $::config->get("preop.securitydomain.kra$count.secureport");
      }
      if (($host eq "") || ($port eq "")) {
        $::symbol{errorString} = "no DRM found.  CA, TKS and DRM must be installed prior to TPS installation";
        return 0;
      }

      $::config->put("preop.krainfo.select", "https://$host:$port");
      my $subsystemCertNickName = $::config->get("preop.cert.subsystem.nickname");
      $::config->put("conn.drm1.clientNickname", $subsystemCertNickName);
      $::config->put("conn.drm1.hostport", $host . ":" . $port); 
      $::config->put("conn.tks1.serverKeygen", "true");
      $::config->put("op.enroll.userKey.keyGen.encryption.serverKeygen.enable", "true");
      $::config->put("op.enroll.userKeyTemporary.keyGen.encryption.serverKeygen.enable", "true");
    } else { 
      # no keygen
      $::config->put("conn.tks1.serverKeygen", "false");
      $::config->put("op.enroll.userKey.keyGen.encryption.serverKeygen.enable", "false");
      $::config->put("op.enroll.userKeyTemporary.keyGen.encryption.serverKeygen.enable", "false");
      $::config->put("conn.drm1.clientNickname", "");
      $::config->put("conn.drm1.hostport", "");
    }
    $::config->commit();

    return 1;
}

sub display
{
    my ($q) = @_;
    &PKI::TPS::Wizard::debug_log("DRMInfoPanel: display");

    $::symbol{urls}        = [];
    my $count = 0;
    while (1) {
      my $host = $::config->get("preop.securitydomain.kra$count.host");
      if ($host eq "") {
        goto DONE;
      }
      my $port = $::config->get("preop.securitydomain.kra$count.secureport");
      my $name = $::config->get("preop.securitydomain.kra$count.subsystemname");
      $::symbol{urls}[$count++] = $name . " - https://" . $host . ":" . $port;
    }
DONE:
    $::symbol{urls_size}   = $count;

    return 1;
}

1;
