#!/usr/bin/perl
#
# --- BEGIN COPYRIGHT BLOCK ---
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; version 2 of the License.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License along
# with this program; if not, write to the Free Software Foundation, Inc.,
# 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Copyright (C) 2007 Red Hat, Inc.
# All rights reserved.
# --- END COPYRIGHT BLOCK ---
#
#
#
#

package op;
use lib $ENV{DOCUMENT_ROOT} . "/../lib/perl";

use CGI;
use PKI::Service::Op;
use PKI::Base::Conf;
use PKI::Base::Util;
use PKI::Request::Queue;
use Template::Velocity;
use PKI::Base::Registry;

use vars qw (@ISA);
use PKI::Service::Op;
@ISA = qw(PKI::Service::Op);

sub new {
  my $self = {};
  bless ($self);
  return $self;
}

sub process()
{
  my $self = shift;
  my $q = CGI->new();

  my $util = PKI::Base::Util->new();

  my $server_id = $util->get_val($q->param('server_id'));
  my $site_id = $util->get_val($q->param('site_id'));
  my $email = $util->get_val($q->param('email'));
  my $csr = $util->get_val($q->param('csr'));

  $csr = $util->normalize_csr($csr);

  my $docroot = PKI::Base::Registry->get_docroot();
  my $parser = PKI::Base::Registry->get_parser();
  my $cfg = PKI::Base::Registry->get_config();

  $self->debug_params($cfg, $q);

  my $queue = PKI::Request::Queue->new();
  $queue->open($cfg);
  my $request_id = $queue->create_request("server", 
                            "server_id=" . $server_id . ";" .  
                            "site_id=" . $site_id . ";" .  
                            "csr=" . $csr,
                            "0",
                            $email);
  my %context;
  $context{request_id} = $request_id;
  $self->debug_log($cfg, "request $request_id created");
  $queue->close();

  my $result = $parser->execute_file_with_context("ee/server/submit.vm",
                          \%context);

  my $xml = $q->param('xml');
  if ($xml eq "true") {
    print "Content-Type: text/xml\n\n";
    print $self->xml_output(\%context);
  } else {
    print "Content-Type: text/html\n\n";
    print "$result";
  }
}

my $op = op->new();
$op->execute();
