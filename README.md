# Overview

This project contains code for syncing ldap users to gsuite service.

This service makes some assumptations:
* GSuite login always looks like <user_id>@<gsuite-domain>.
Also this means that each user should have gsuite login as one of mail addresses - it will be used as primary mail address.
* User name is taken from 'givenName' field and family name is taken from 'sn' field.
* Password should be always in SHA hashed form (google does not support any other form).
And ldap reader login should be able to read password hashes from ldap.
* All mail addresses except of primary will be added as aliases.
* Phone number will be taken from 'mobile' field in ldap.
